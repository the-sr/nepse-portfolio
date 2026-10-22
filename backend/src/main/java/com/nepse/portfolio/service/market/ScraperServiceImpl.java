package com.nepse.portfolio.service.market;

import lombok.extern.slf4j.Slf4j;
import com.nepse.portfolio.service.market.ScraperService;
import com.nepse.portfolio.domain.ScrapeRun;
import com.nepse.portfolio.repository.ScrapeRunRepository;
import com.nepse.portfolio.utils.scraper.ScrapedQuote;

import com.nepse.portfolio.service.alert.AlertService;
import com.nepse.portfolio.dto.market.MarketDtos.StockDto;
import com.nepse.portfolio.domain.PriceHistory;
import com.nepse.portfolio.repository.PriceHistoryRepository;
import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.repository.StockRepository;
import com.nepse.portfolio.utils.scraper.MarketDataProvider;
import com.nepse.portfolio.utils.nepse.NepseApiClient;
import com.nepse.portfolio.service.market.MarketCalendarService;
import com.nepse.portfolio.service.market.StreamService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ScraperServiceImpl implements ScraperService {


    private final List<MarketDataProvider> providers;
    private final StockRepository stockRepository;
    private final PriceHistoryRepository historyRepository;
    private final ScrapeRunRepository runRepository;
    private final AlertService alertService;
    private final StreamService streamService;
    private final TransactionTemplate transaction;
    private final ReentrantLock lock = new ReentrantLock();
    private final NepseApiClient nepseApiClient;
    private final MarketCalendarService marketCalendarService;

    private final boolean enabled;
    private final ZoneId zone;
    /** Session window (config-driven): fetches only run inside it (unless forced). */
    private final LocalTime activeFrom;
    private final LocalTime activeTo;

    /** Market status from NEPSE's API is cached so the scheduler does not hammer it. */
    private static final long STATUS_TTL_MS = 60_000;
    private volatile MarketStatus statusCache;
    private volatile long statusFetchedAtMs;

    public ScraperServiceImpl(List<MarketDataProvider> providers,
                          StockRepository stockRepository,
                          PriceHistoryRepository historyRepository,
                          ScrapeRunRepository runRepository,
                          AlertService alertService,
                          StreamService streamService,
                          PlatformTransactionManager transactionManager,
                          NepseApiClient nepseApiClient,
                          MarketCalendarService marketCalendarService,
                          @Value("${app.scraper.enabled:true}") boolean enabled,
                          @Value("${app.scraper.timezone:Asia/Kathmandu}") String timezone,
                          @Value("${app.scraper.active-from:11:00}") String activeFrom,
                          @Value("${app.scraper.active-to:15:00}") String activeTo,
                          @Value("${app.market.providers:nepse,sharesansar}") String providerOrder) {
        this.stockRepository = stockRepository;
        this.historyRepository = historyRepository;
        this.runRepository = runRepository;
        this.alertService = alertService;
        this.streamService = streamService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.nepseApiClient = nepseApiClient;
        this.marketCalendarService = marketCalendarService;
        this.enabled = enabled;
        this.zone = ZoneId.of(timezone);
        this.activeFrom = LocalTime.parse(activeFrom);
        this.activeTo = LocalTime.parse(activeTo);
        this.providers = orderProviders(providers, providerOrder);
        if (this.providers.isEmpty()) {
            log.warn("No market data providers are enabled; live prices will not update");
        } else {
            log.info("Market data providers (in order): {}",
                    this.providers.stream().map(MarketDataProvider::name).toList());
        }
    }

    public Map<String, Object> scrape(boolean force) {
        if (!enabled) {
            return status("Scraper is disabled by configuration");
        }
        if (!lock.tryLock()) {
            return status("A scrape is already running");
        }

        // Session window (default 11:00-15:00 Nepal time): periodic runs make no
        // requests while NEPSE is closed, saving battery/egress. Admin "force"
        // bypasses the window; the dedupe still protects every store path.
        if (!force && !inSession()) {
            return status("skipped: outside NEPSE session window "
                    + activeFrom.toString() + "-" + activeTo.toString());
        }

        MarketStatus market = marketStatus();
        ScrapeRun run = new ScrapeRun("auto");
        try {
            FetchOutcome outcome = fetchQuotes();
            run.setSource(outcome.provider());

            ApplyResult result = transaction.execute(status -> applyQuotes(outcome.quotes()));
            if (result == null) {
                throw new IllegalStateException("Quote update was rolled back");
            }

            run.finish(true, outcome.quotes().size(),
                    "Matched " + result.matched() + " of " + outcome.quotes().size()
                            + " quotes from " + outcome.provider()
                            + "; " + result.updated() + " updated, "
                            + result.unchanged() + " unchanged"
                            + (market.open() ? "" : " (market closed)"));
            runRepository.save(run);

            if (!result.dtos().isEmpty()) {
                streamService.broadcastPrices(result.dtos());
                alertService.evaluate(result.stocks());
            }

            log.info("Market data refreshed from {}: {} quotes, {} matched, {} updated, {} unchanged{}",
                    outcome.provider(), outcome.quotes().size(), result.matched(),
                    result.updated(), result.unchanged(), market.open() ? "" : " (market closed)");
            return status(null);
        } catch (Exception ex) {
            run.finish(false, 0, truncate(ex.getMessage(), 950));
            runRepository.save(run);
            log.warn("Market data refresh failed: {}", ex.getMessage());
            return status(ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Tries each configured provider until one returns quotes. A provider that
     * throws or returns nothing is logged and skipped.
     */
    private FetchOutcome fetchQuotes() {
        List<String> failures = new ArrayList<>();
        for (MarketDataProvider provider : providers) {
            try {
                List<ScrapedQuote> quotes = provider.fetch();
                if (quotes == null || quotes.isEmpty()) {
                    failures.add(provider.name() + ": empty response");
                    log.warn("Market data provider '{}' returned no quotes", provider.name());
                    continue;
                }
                return new FetchOutcome(provider.name(), quotes);
            } catch (Exception ex) {
                failures.add(provider.name() + ": " + ex.getMessage());
                log.warn("Market data provider '{}' failed: {}", provider.name(), ex.getMessage());
            }
        }
        throw new IllegalStateException("All market data providers failed -> " + String.join("; ", failures));
    }

    private ApplyResult applyQuotes(List<ScrapedQuote> quotes) {
        Map<String, Stock> stocks = stockRepository.findAll().stream()
                .collect(Collectors.toMap(s -> s.getSymbol().toUpperCase(Locale.ROOT),
                        Function.identity()));

        LocalDate today = LocalDate.now(zone);
        LocalDate oldest = quotes.stream()
                .map(q -> q.asOf() == null ? today : q.asOf())
                .min(Comparator.naturalOrder())
                .orElse(today);

        List<Long> ids = stocks.values().stream().map(Stock::getId).toList();
        Map<BarKey, PriceHistory> existingBars = historyRepository
                .findByStockIdInAndTradeDateBetweenOrderByTradeDateAsc(ids, oldest, today)
                .stream()
                .collect(Collectors.toMap(h -> new BarKey(h.getStockId(), h.getTradeDate()),
                        Function.identity(), (first, second) -> first));

        List<Stock> updated = new ArrayList<>();
        int matched = 0;
        int updatedCount = 0;
        int unchanged = 0;

        for (ScrapedQuote quote : quotes) {
            Stock stock = stocks.get(quote.symbol().toUpperCase(Locale.ROOT));
            if (stock == null) {
                continue;
            }
            matched++;

            // Store only what is actually new: identical data is skipped so that
            // runs outside the session (or off-calendar runs) write nothing.
            if (!hasChanged(stock, quote)) {
                unchanged++;
                continue;
            }
            updatedCount++;

            LocalDate barDate = quote.asOf() == null || quote.asOf().isAfter(today) ? today : quote.asOf();

            stock.setLtp(quote.ltp());
            stock.setPrevClose(quote.prevClose());
            stock.setOpenPrice(quote.open());
            stock.setHighPrice(quote.high());
            stock.setLowPrice(quote.low());
            stock.setVolume(quote.volume());
            stock.setTurnover(quote.turnover());
            stock.setSource("SCRAPED");
            stock.touch();
            updated.add(stock);
            matched++;

            BarKey key = new BarKey(stock.getId(), barDate);
            PriceHistory bar = existingBars.get(key);
            if (bar == null) {
                bar = new PriceHistory(stock.getId(), barDate, quote.open(), quote.high(),
                        quote.low(), quote.ltp(), quote.volume());
                historyRepository.save(bar);
                existingBars.put(key, bar);
            } else {
                bar.setOpenPrice(quote.open());
                bar.setHighPrice(quote.high());
                bar.setLowPrice(quote.low());
                bar.setClosePrice(quote.ltp());
                bar.setVolume(quote.volume());
            }
        }

        stockRepository.saveAll(updated);
        return new ApplyResult(updated, updated.stream().map(StockDto::from).toList(),
                matched, updatedCount, unchanged);
    }

    /**
     * True when the quote carries anything new for the stock row. Every scalar
     * is compared so a quiet post-session sweep against yesterday's data is a
     * no-op instead of re-writing the same values over and over.
     */
    static boolean hasChanged(Stock stock, ScrapedQuote quote) {
        return differs(stock.getLtp(), quote.ltp())
                || differs(stock.getOpenPrice(), quote.open())
                || differs(stock.getHighPrice(), quote.high())
                || differs(stock.getLowPrice(), quote.low())
                || differs(stock.getPrevClose(), quote.prevClose())
                || differs(stock.getTurnover(), quote.turnover())
                || stock.getVolume() != quote.volume();
    }

    /** True when, at this instant (in NEPSE's timezone), we are inside the configured window. */
    private boolean inSession() {
        LocalTime now = LocalTime.now(zone);
        return !now.isBefore(activeFrom) && !now.isAfter(activeTo);
    }

    private static boolean differs(BigDecimal one, BigDecimal other) {
        if (one == null || other == null) {
            return one != other;
        }
        return one.compareTo(other) != 0;
    }

    private MarketStatus marketStatus() {
        long now = System.currentTimeMillis();
        MarketStatus cached = statusCache;
        if (cached != null && now - statusFetchedAtMs < STATUS_TTL_MS) {
            return cached;
        }
        Boolean open = nepseApiClient.fetchMarketStatus().orElse(null);
        MarketStatus resolved = open != null
                ? new MarketStatus(open, StatusSource.API)
                : new MarketStatus(marketCalendarService.isMarketOpen(), StatusSource.CALENDAR);
        statusFetchedAtMs = now;
        statusCache = resolved;
        return resolved;
    }

    @Override
    public boolean isMarketOpen() {
        // Calendar-only check; NEPSE's own verdict is reported through the
        // "marketStatus" entry of the status payload.
        return marketCalendarService.isMarketOpen();
    }

    public Map<String, Object> status(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", enabled);
        payload.put("marketOpen", isMarketOpen());
        payload.put("timezone", zone.getId());
        payload.put("totalStocks", stockRepository.count());
        payload.put("activeStocks", stockRepository.countByActiveTrue());
        payload.put("liveStocks", stockRepository.countBySource("SCRAPED"));
        payload.put("providers", providers.stream().map(p -> Map.of(
                "name", p.name(),
                "description", p.description())).toList());

        MarketStatus market = marketStatus();
        payload.put("marketStatus", Map.of(
                "open", market.open(),
                "source", market.source().name()));
        MarketCalendarService.CalendarView calendar = marketCalendarService.view();
        payload.put("calendar", Map.of(
                "tradingDays", calendar.tradingDays(),
                "openTime", calendar.openTime(),
                "closeTime", calendar.closeTime(),
                "source", calendar.source()));
        if (message != null) {
            payload.put("message", message);
        }

        runRepository.findTopByOrderByStartedAtDesc().ifPresent(run -> {
            payload.put("source", run.getSource());
            payload.put("lastAttempt", run.getStartedAt());
            payload.put("lastSuccess", run.isSuccess());
            payload.put("lastRows", run.getRowsParsed());
            payload.put("lastMessage", run.getMessage());
        });

        List<Map<String, Object>> recent = new ArrayList<>();
        for (ScrapeRun run : runRepository.findTop20ByOrderByStartedAtDesc()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", run.getSource());
            item.put("startedAt", run.getStartedAt());
            item.put("finishedAt", run.getFinishedAt());
            item.put("success", run.isSuccess());
            item.put("rows", run.getRowsParsed());
            item.put("message", run.getMessage());
            recent.add(item);
        }
        payload.put("recent", recent);
        return payload;
    }

    private List<MarketDataProvider> orderProviders(List<MarketDataProvider> available, String order) {
        Map<String, Integer> rank = new HashMap<>();
        String[] names = order == null ? new String[0] : order.split(",");
        for (int i = 0; i < names.length; i++) {
            String name = names[i].trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                rank.putIfAbsent(name, i);
            }
        }
        return available.stream()
                .sorted(Comparator.comparingInt(p -> rank.getOrDefault(p.name(), 1000)))
                .toList();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "Unknown error";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record FetchOutcome(String provider, List<ScrapedQuote> quotes) {
    }

    private record ApplyResult(List<Stock> stocks, List<StockDto> dtos, int matched,
                               int updated, int unchanged) {
    }

    private enum StatusSource {
        API, CALENDAR
    }

    private record MarketStatus(boolean open, StatusSource source) {
    }

    private record BarKey(Long stockId, LocalDate tradeDate) {
    }
}
