package com.nepse.portfolio.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nepse.portfolio.service.market.NepsePriceHistoryService;
import com.nepse.portfolio.service.market.NepseStockSyncService;
import com.nepse.portfolio.service.market.ScraperService;
import com.nepse.portfolio.service.market.StreamService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledService {

    private final ScraperService scraperService;
    private final NepsePriceHistoryService priceHistoryService;
    private final NepseStockSyncService nepseStockSyncService;
    private final StreamService streamService;

    @Value("${app.nepse.enabled:true}")
    private boolean nepseEnabled;

    @Value("${app.history.backfill.auto:true}")
    private boolean historyAuto;

    @Value("${app.history.backfill.days:260}")
    private int historyDays;

    @Scheduled(fixedDelayString = "${app.scraper.interval-ms:90000}", initialDelayString = "15000")
    public void refreshPrices() {
        scraperService.scrape(false);
    }

    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Kathmandu")
    public void repairHistoryGaps() {
        if (!historyAuto) {
            return;
        }
        try {
            priceHistoryService.repairIfStale(historyDays).ifPresentOrElse(
                    result -> log.info("Nightly history repair: {} files, {} bars written ({}..{})",
                            result.filesFetched(), result.barsWritten(), result.from(), result.to()),
                    () -> log.debug("Nightly history check: nothing missing"));
        } catch (Exception ex) {
            log.warn("Nightly history repair failed: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kathmandu")
    public void syncStocksAtMidnight() {
        if (!nepseEnabled) {
            return;
        }
        try {
            NepseStockSyncService.Result result = nepseStockSyncService.syncStocks();
            log.info("Midnight stock sync: {} securities, {} added, {} updated, {} deactivated",
                    result.activeSecurities(), result.added(), result.updated(), result.deactivated());
        } catch (Exception ex) {
            log.warn("Midnight stock sync failed: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedRate = 20000)
    public void heartbeat() {
        streamService.heartbeat();
    }
}