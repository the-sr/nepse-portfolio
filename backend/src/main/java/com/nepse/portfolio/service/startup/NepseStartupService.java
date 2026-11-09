package com.nepse.portfolio.service.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nepse.portfolio.service.market.NepsePriceHistoryService;
import com.nepse.portfolio.service.market.NepseStockSyncService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class NepseStartupService implements CommandLineRunner {

    private final NepseStockSyncService nepseStockSyncService;
    private final NepsePriceHistoryService priceHistoryService;

    @Value("${app.nepse.enabled:true}")
    private boolean nepseEnabled;

    @Value("${app.history.backfill.auto:true}")
    private boolean historyAuto;

    @Value("${app.history.backfill.days:1250}")
    private int historyDays;

    @Value("${app.history.backfill.all:false}")
    private boolean historyAll;

    @Override
    public void run(String... args) {
        if (nepseEnabled) {
            try {
                NepseStockSyncService.Result result = nepseStockSyncService.syncStocks();
                log.info("Startup stock sync: {} securities, {} added, {} updated, {} deactivated",
                        result.activeSecurities(), result.added(), result.updated(), result.deactivated());
            } catch (Exception ex) {
                log.warn("Startup stock sync failed; continuing with the existing stock table: {}", ex.getMessage());
            }
        } else {
            log.info("NEPSE stock sync on startup is disabled (app.nepse.enabled=false)");
        }

        if (!historyAuto) {
            log.info("History backfill auto-maintenance is disabled (app.history.backfill.auto=false)");
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                if (historyAll) {
                    NepsePriceHistoryService.Result result = priceHistoryService.backfillAll();
                    log.info("Startup history backfill (all): {} files, {} bars written ({}..{}), {} replaced, {} hydrated",
                            result.filesFetched(), result.barsWritten(), result.from(), result.to(),
                            result.barsReplaced(), result.pricesBackfilled());
                } else {
                    priceHistoryService.repairIfStale(historyDays).ifPresentOrElse(
                            result -> log.info("Startup history backfill: {} files, {} bars written ({}..{}), {} replaced",
                                    result.filesFetched(), result.barsWritten(), result.from(), result.to(), result.barsReplaced()),
                            () -> log.info("History check: price history is already current"));
                }
            } catch (Exception ex) {
                log.warn("Startup history backfill failed: {}", ex.getMessage());
            }
        }, "history-backfill-starter");
        worker.setDaemon(true);
        worker.start();
    }
}