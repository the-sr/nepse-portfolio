package com.nepse.portfolio.market.scraper;

import com.nepse.portfolio.alert.AlertService;
import com.nepse.portfolio.market.MarketDtos.StockDto;
import com.nepse.portfolio.market.PriceHistory;
import com.nepse.portfolio.market.PriceHistoryRepository;
import com.nepse.portfolio.market.Stock;
import com.nepse.portfolio.market.StockRepository;
import com.nepse.portfolio.stream.StreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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

    private final SharesansarScraper scraper;
    private final StockRepository stockRepository;
    private final PriceHistoryRepository historyRepository;
    private final ScrapeRunRepository runRepository;
    private final AlertService alertService;
    private final StreamService streamService;
    private final ReentrantLock lock = new ReentrantLock();

    private final boolean enabled;
    private final boolean ignoreMarketHours;
    private final ZoneId zone;
    private final LocalTime marketOpen;
    private final LocalTime marketClose;

    public ScraperService(SharesansarScraper scraper,
                          StockRepository stockRepository,
                          PriceHistoryRepository historyRepository,
                          ScrapeRunRepository runRepository,
                          AlertService alertService,
                          StreamService streamService,
                          @Value("${app.scraper.enabled:true}") boolean enabled,
                          @Value("${app.scraper.ignore-market-hours:false}") boolean ignoreMarketHours,
                          @Value("${app.scraper.timezone:Asia/Kathmandu}") String timezone,
                          @Value("${app.scraper.market-open:11:00}") String marketOpen,
                          @Value("${app.scraper.market-close:15:00}") String marketClose) {
        this.scraper = scraper;
        this.stockRepository = stockRepository;
        this.historyRepository = historyRepository;
        this.runRepository = runRepository;
        this.alertService = alertService;
        this.streamService = streamService;
        this.enabled = enabled;
        this.ignoreMarketHours = ignoreMarketHours;
        this.zone = ZoneId.of(timezone);
        this.marketOpen = LocalTime.parse(marketOpen);
        this.marketClose = LocalTime.parse(marketClose);
    }

    @Transactional
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

        ScrapeRun run = new ScrapeRun("sharesansar");
        try {
            List<ScrapedQuote> quotes = scraper.fetch();
            if (quotes.isEmpty()) {
                throw new IllegalStateException("No quotes parsed; the page layout may have changed");
            }

            Map<String, Stock> stocks = stockRepository.findAll().stream()
                    .collect(Collectors.toMap(s -> s.getSymbol().toUpperCase(Locale.ROOT),
                            Function.identity()));
            LocalDate today = LocalDate.now(zone);
            List<Long> ids = stocks.values().stream().map(Stock::getId).toList();
            Map<Long, PriceHistory> todayBars = historyRepository
                    .findByStockIdInAndTradeDateBetweenOrderByTradeDateAsc(ids, today, today)
                    .stream()
                    .collect(Collectors.toMap(PriceHistory::getStockId, Function.identity()));

            List<Stock> updated = new ArrayList<>();
            int matched = 0;
            for (ScrapedQuote quote : quotes) {
                Stock stock = stocks.get(quote.symbol().toUpperCase(Locale.ROOT));
                if (stock == null) {
                    continue;
                }
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

                PriceHistory bar = todayBars.get(stock.getId());
                if (bar == null) {
                    bar = new PriceHistory(stock.getId(), today, quote.open(), quote.high(),
                            quote.low(), quote.ltp(), quote.volume());
                    historyRepository.save(bar);
                    todayBars.put(stock.getId(), bar);
                } else {
                    bar.setOpenPrice(quote.open());
                    bar.setHighPrice(quote.high());
                    bar.setLowPrice(quote.low());
                    bar.setClosePrice(quote.ltp());
                    bar.setVolume(quote.volume());
                }
            }

            stockRepository.saveAll(updated);
            run.finish(true, quotes.size(), "Matched " + matched + " of " + quotes.size() + " scraped quotes");
            runRepository.save(run);

            streamService.broadcastPrices(updated.stream().map(StockDto::from).toList());
            alertService.evaluate(updated);
            log.info("Scrape complete: {} quotes, {} matched", quotes.size(), matched);
            return status(null);
        } catch (Exception ex) {
            run.finish(false, 0, truncate(ex.getMessage(), 950));
            runRepository.save(run);
            log.warn("Scrape failed: {}", ex.getMessage());
            return status(ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public boolean isMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(marketOpen) && !time.isAfter(marketClose);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", enabled);
        payload.put("marketOpen", isMarketOpen());
        payload.put("timezone", zone.getId());
        payload.put("totalStocks", stockRepository.count());
        payload.put("liveStocks", stockRepository.countBySource("SCRAPED"));
        payload.put("source", "Sharesansar live trading");
        if (message != null) {
            payload.put("message", message);
        }

        runRepository.findTopByOrderByStartedAtDesc().ifPresent(run -> {
            payload.put("lastAttempt", run.getStartedAt());
            payload.put("lastSuccess", run.isSuccess());
            payload.put("lastRows", run.getRowsParsed());
            payload.put("lastMessage", run.getMessage());
        });

        List<Map<String, Object>> recent = new ArrayList<>();
        for (ScrapeRun run : runRepository.findTop20ByOrderByStartedAtDesc()) {
            Map<String, Object> item = new LinkedHashMap<>();
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

    private String truncate(String value, int max) {
        if (value == null) {
            return "Unknown error";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
