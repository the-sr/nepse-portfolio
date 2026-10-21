package com.nepse.portfolio.service.impl;

import com.nepse.portfolio.service.HistoryBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety-net at the end of the session: if the app was down (or a scrape
 * failed) so that the newest stored bar is older than the last trading day,
 * repair exactly the missing slice from the open dataset. A database that is
 * current costs one tiny query per day.
 */
@Component
public class HistoryMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(HistoryMaintenanceScheduler.class);

    private final HistoryBackfillService historyBackfillService;
    private final boolean enabled;
    private final int windowDays;

    public HistoryMaintenanceScheduler(HistoryBackfillService historyBackfillService,
                                       @Value("${app.history.backfill.auto:true}") boolean enabled,
                                       @Value("${app.history.backfill.days:260}") int windowDays) {
        this.historyBackfillService = historyBackfillService;
        this.enabled = enabled;
        this.windowDays = windowDays;
    }

    /** 15:40 Kathmandu, Mon-Fri; weekends are no-ops by construction. */
    @Scheduled(cron = "${app.history.backfill.repair-cron:0 40 15 * * MON-FRI}",
            zone = "${app.scraper.timezone:Asia/Kathmandu}")
    public void repairGaps() {
        if (!enabled) {
            return;
        }
        try {
            historyBackfillService.repairIfStale(windowDays).ifPresentOrElse(
                    result -> log.info("Nightly history repair: {} files, {} bars written ({}..{})",
                            result.filesFetched(), result.barsWritten(), result.from(), result.to()),
                    () -> log.debug("Nightly history check: nothing missing"));
        } catch (Exception ex) {
            log.warn("Nightly history repair failed: {}", ex.getMessage());
        }
    }
}
