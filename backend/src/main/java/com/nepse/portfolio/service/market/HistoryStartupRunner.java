package com.nepse.portfolio.service.market;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nepse.portfolio.service.market.HistoryBackfillService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fills a fresh database with real history automatically, the same way the
 * universe import populates the stock list. Runs after
 * {@link UniverseStartupRunner} ({@code @Order(2)}), off the startup thread so
 * a slow dataset never delays booting.
 *
 * <p>{@code repairIfStale} is gap-aware: a database that is already current
 * costs one cheap query, a database that missed a few sessions is repaired
 * just for the gap.</p>
 */
@Component
@Order(2)
@Slf4j
@RequiredArgsConstructor
public class HistoryStartupRunner implements CommandLineRunner {


    private final HistoryBackfillService historyBackfillService;

    @Value("${app.history.backfill.auto:true}")
    private boolean enabled;
    @Value("${app.history.backfill.days:260}")
    private int windowDays;

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("History backfill auto-maintenance is disabled "
                    + "(app.history.backfill.auto=false)");
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                historyBackfillService.repairIfStale(windowDays).ifPresentOrElse(
                        result -> log.info("Startup history backfill: {} files, {} bars written "
                                        + "({}..{}), {} replaced",
                                result.filesFetched(), result.barsWritten(),
                                result.from(), result.to(), result.barsReplaced()),
                        () -> log.info("History check: price history is already current"));
            } catch (Exception ex) {
                log.warn("Startup history backfill failed (run POST /api/admin/market/backfill "
                        + "to retry manually): {}", ex.getMessage());
            }
        }, "history-backfill-starter");
        worker.setDaemon(true);
        worker.start();
    }
}
