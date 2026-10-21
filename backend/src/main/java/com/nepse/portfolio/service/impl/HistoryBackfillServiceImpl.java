package com.nepse.portfolio.service.impl;

import com.nepse.portfolio.service.HistoryBackfillService;
import com.nepse.portfolio.domain.User;
import com.nepse.portfolio.utils.SocrateaiCsv;

import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Replaces the synthetic price history with real NEPSE data from the
 * socrateai-official/nepse-open-data dataset (MIT licensed).
 *
 * <p>The dataset publishes one CSV per trading day containing every symbol.
 * Files for exchange holidays simply do not exist and are skipped.</p>
 */
@Service
public class HistoryBackfillServiceImpl implements HistoryBackfillService {

    private static final Logger log = LoggerFactory.getLogger(HistoryBackfillServiceImpl.class);

    private static final int MAX_DAYS = 2000;
    private static final int BATCH_SIZE = 1000;
    private static final String INSERT_SQL = """
            insert into price_history
                (stock_id, trade_date, open_price, high_price, low_price, close_price, volume)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * Securities that did not trade on the most recent session have no live
     * quote, so the feed leaves a stale or zero price behind. Copy the latest
     * two real bars onto the stock row instead. Live-scraped rows are never
     * touched.
     */
    private static final String HYDRATE_SQL = """
            with ranked as (
                select stock_id, open_price, high_price, low_price, close_price, volume,
                       row_number() over (partition by stock_id order by trade_date desc) as rn
                from price_history
            ), latest as (
                select stock_id,
                       max(open_price)  filter (where rn = 1) as open1,
                       max(high_price)  filter (where rn = 1) as high1,
                       max(low_price)   filter (where rn = 1) as low1,
                       max(close_price) filter (where rn = 1) as close1,
                       max(close_price) filter (where rn = 2) as close2,
                       max(volume)      filter (where rn = 1) as volume1
                from ranked
                where rn <= 2
                group by stock_id
            )
            update stocks s
               set ltp = l.close1,
                   prev_close = coalesce(l.close2, l.close1),
                   open_price = l.open1,
                   high_price = l.high1,
                   low_price = l.low1,
                   volume = coalesce(l.volume1, 0),
                   source = 'HISTORY'
              from latest l
             where s.id = l.stock_id
               and s.source in ('NEPSE', 'HISTORY', 'SEEDED')
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final StockRepository stockRepository;
    private final HttpClient http;
    private final String baseUrl;
    private final int parallelism;
    private final ZoneId zone;

    public HistoryBackfillServiceImpl(JdbcTemplate jdbc,
                                  PlatformTransactionManager transactionManager,
                                  StockRepository stockRepository,
                                  @Value("${app.history.backfill.base-url:https://raw.githubusercontent.com/socrateai-official/nepse-open-data/main}") String baseUrl,
                                  @Value("${app.history.backfill.parallelism:6}") int parallelism,
                                  @Value("${app.scraper.timezone:Asia/Kathmandu}") String timezone) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.stockRepository = stockRepository;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.parallelism = Math.max(1, parallelism);
        this.zone = ZoneId.of(timezone);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Result backfill(int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_DAYS);
        }

        List<LocalDate> candidates = tradingDates(days);
        Map<LocalDate, List<SocrateaiCsv.Row>> data = new ConcurrentHashMap<>();
        List<String> missing = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(parallelism, runnable -> {
            Thread thread = new Thread(runnable, "history-backfill");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<CompletableFuture<Void>> tasks = candidates.stream()
                    .map(date -> CompletableFuture.runAsync(() -> load(date, data, missing), pool))
                    .toList();
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        } finally {
            pool.shutdown();
        }

        // Holidays return 404, so look further back than requested and keep the
        // most recent `days` files that actually exist.
        List<LocalDate> fetchedDescending = new ArrayList<>(data.keySet());
        fetchedDescending.sort(Comparator.reverseOrder());
        for (LocalDate extra : fetchedDescending.subList(Math.min(days, fetchedDescending.size()),
                fetchedDescending.size())) {
            data.remove(extra);
        }

        // Fail loudly if the dataset moved or the network is unusable, rather than
        // silently wiping history and inserting a handful of rows.
        int minimumAccepted = Math.max(1, candidates.size() / 3);
        if (data.size() < minimumAccepted) {
            throw new IllegalStateException("Only " + data.size() + " of " + candidates.size()
                    + " daily files could be fetched from " + baseUrl
                    + "; refusing to replace price history");
        }

        Result result = transaction.execute(status -> persist(data, days, candidates.size(), missing));
        if (result == null) {
            throw new IllegalStateException("Backfill was rolled back");
        }
        return result;
    }

    private void load(LocalDate date,
                      Map<LocalDate, List<SocrateaiCsv.Row>> data,
                      List<String> missing) {
        String url = baseUrl + "/ohlc_adjusted_stock/adj_" + date + ".csv";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "nepse-portfolio/1.0")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return; // exchange holiday
            }
            if (response.statusCode() != 200) {
                missing.add(date.toString());
                log.warn("Backfill file for {} returned HTTP {}", date, response.statusCode());
                return;
            }
            List<SocrateaiCsv.Row> rows = SocrateaiCsv.parse(response.body());
            if (rows.isEmpty()) {
                missing.add(date.toString());
                return;
            }
            data.put(date, rows);
        } catch (IOException ex) {
            missing.add(date.toString());
            log.warn("Could not download backfill file for {}: {}", date, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            missing.add(date.toString());
        }
    }

    private Result persist(Map<LocalDate, List<SocrateaiCsv.Row>> data,
                           int daysRequested,
                           int filesRequested,
                           List<String> missing) {
        Map<String, Stock> bySymbol = stockRepository.findAll().stream()
                .collect(Collectors.toMap(s -> s.getSymbol().toUpperCase(Locale.ROOT),
                        Function.identity(), (first, second) -> first));

        List<Object[]> bars = new ArrayList<>();
        Set<String> unknown = new LinkedHashSet<>();
        Set<String> matchedSymbols = new HashSet<>();
        LocalDate min = null;
        LocalDate max = null;

        for (List<SocrateaiCsv.Row> rows : data.values()) {
            for (SocrateaiCsv.Row row : rows) {
                Stock stock = bySymbol.get(row.symbol());
                if (stock == null) {
                    unknown.add(row.symbol());
                    continue;
                }
                bars.add(new Object[]{stock.getId(), row.date(), row.open(), row.high(),
                        row.low(), row.close(), row.volume()});
                matchedSymbols.add(row.symbol());
                if (min == null || row.date().isBefore(min)) {
                    min = row.date();
                }
                if (max == null || row.date().isAfter(max)) {
                    max = row.date();
                }
            }
        }

        if (bars.isEmpty()) {
            throw new IllegalStateException("No dataset rows matched symbols in the stocks table");
        }

        // Remove everything from the start of the imported window onwards. That
        // covers the imported range and any synthetic bars the demo seeder created
        // after the dataset's most recent trading day, while preserving genuinely
        // older history from a previous, larger import.
        int deleted = jdbc.update("delete from price_history where trade_date >= ?", min);

        AtomicInteger written = new AtomicInteger();
        for (int start = 0; start < bars.size(); start += BATCH_SIZE) {
            List<Object[]> chunk = bars.subList(start, Math.min(bars.size(), start + BATCH_SIZE));
            jdbc.batchUpdate(INSERT_SQL, chunk);
            written.addAndGet(chunk.size());
        }

        int hydrated = jdbc.update(HYDRATE_SQL);

        List<String> unknownSamples = unknown.stream().limit(15).toList();
        List<String> missingSamples = List.copyOf(missing).stream().limit(15).toList();

        log.info("History backfill: {} bars for {} symbols ({}..{}), replaced {} rows, {} unknown symbols, hydrated {} prices",
                written.get(), matchedSymbols.size(), min, max, deleted, unknown.size(), hydrated);

        return new Result(daysRequested, data.size(), Math.max(0, filesRequested - data.size()),
                missingSamples, written.get(), matchedSymbols.size(), unknown.size(),
                unknownSamples, deleted, hydrated, min, max);
    }

    private List<LocalDate> tradingDates(int days) {
        // Ask for a few extra weekdays so holidays do not shorten the history.
        int wanted = days + Math.max(20, days / 4);
        List<LocalDate> dates = new ArrayList<>(wanted);
        LocalDate date = LocalDate.now(zone);
        while (dates.size() < wanted) {
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
            date = date.minusDays(1);
        }
        Collections.reverse(dates);
        return dates;
    }

    private String stripTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
