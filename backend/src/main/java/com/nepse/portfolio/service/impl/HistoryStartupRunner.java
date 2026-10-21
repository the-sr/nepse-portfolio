package com.nepse.portfolio.service.impl;

import com.nepse.portfolio.service.HistoryBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class HistoryStartupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoryStartupRunner.class);

    private final HistoryBackfillService historyBackfillService;
    private final boolean enabled;
    private final int windowDays;

    public HistoryStartupRunner(HistoryBackfillService historyBackfillService,
                                @Value("${app.history.backfill.auto:true}") boolean enabled,
                                @Value("${app.history.backfill.days:260}") int windowDays) {
        this.historyBackfillService = historyBackfillService;
        this.enabled = enabled;
        this.windowDays = windowDays;
    }

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
