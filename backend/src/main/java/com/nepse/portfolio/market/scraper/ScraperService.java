package com.nepse.portfolio.market.scraper;

import com.nepse.portfolio.alert.AlertService;
import com.nepse.portfolio.market.MarketDtos.StockDto;
import com.nepse.portfolio.market.PriceHistory;
import com.nepse.portfolio.market.PriceHistoryRepository;
import com.nepse.portfolio.market.Stock;
import com.nepse.portfolio.market.StockRepository;
import com.nepse.portfolio.market.provider.MarketDataProvider;
import com.nepse.portfolio.stream.StreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

    private final List<MarketDataProvider> providers;
    private final StockRepository stockRepository;
    private final PriceHistoryRepository historyRepository;
    private final ScrapeRunRepository runRepository;
    private final AlertService alertService;
    private final StreamService streamService;
    private final TransactionTemplate transaction;
    private final ReentrantLock lock = new ReentrantLock();

    private final boolean enabled;
    private final boolean ignoreMarketHours;
    private final ZoneId zone;
    private final LocalTime marketOpen;
    private final LocalTime marketClose;

    public ScraperService(List<MarketDataProvider> providers,
                          StockRepository stockRepository,
                          PriceHistoryRepository historyRepository,
                          ScrapeRunRepository runRepository,
                          AlertService alertService,
                          StreamService streamService,
                          PlatformTransactionManager transactionManager,
                          @Value("${app.scraper.enabled:true}") boolean enabled,
                          @Value("${app.scraper.ignore-market-hours:false}") boolean ignoreMarketHours,
                          @Value("${app.scraper.timezone:Asia/Kathmandu}") String timezone,
                          @Value("${app.scraper.market-open:11:00}") String marketOpen,
                          @Value("${app.scraper.market-close:15:00}") String marketClose,
                          @Value("${app.market.providers:nepse,sharesansar}") String providerOrder) {
        this.stockRepository = stockRepository;
        this.historyRepository = historyRepository;
        this.runRepository = runRepository;
        this.alertService = alertService;
        this.streamService = streamService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.ignoreMarketHours = ignoreMarketHours;
        this.zone = ZoneId.of(timezone);
        this.marketOpen = LocalTime.parse(marketOpen);
        this.marketClose = LocalTime.parse(marketClose);
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
        if (!force && !ignoreMarketHours && !isMarketOpen()) {
            return status("Market is closed");
        }
        if (!lock.tryLock()) {
            return status("A scrape is already running");
        }

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
                            + " quotes from " + outcome.provider());
            runRepository.save(run);

            streamService.broadcastPrices(result.dtos());
            alertService.evaluate(result.stocks());

            log.info("Market data refreshed from {}: {} quotes, {} matched",
                    outcome.provider(), outcome.quotes().size(), result.matched());
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

        for (ScrapedQuote quote : quotes) {
            Stock stock = stocks.get(quote.symbol().toUpperCase(Locale.ROOT));
            if (stock == null) {
                continue;
            }
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
        return new ApplyResult(updated, updated.stream().map(StockDto::from).toList(), matched);
    }

    public boolean isMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        DayOfWeek day = now.getDayOfWeek();
        // NEPSE currently trades Monday to Friday (verified against the exchange's
        // own data in Oct 2026); only the weekend is closed.
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(marketOpen) && !time.isAfter(marketClose);
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

    private record ApplyResult(List<Stock> stocks, List<StockDto> dtos, int matched) {
    }

    private record BarKey(Long stockId, LocalDate tradeDate) {
    }
}
