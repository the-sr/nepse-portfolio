package com.nepse.portfolio.controller;

import com.nepse.portfolio.service.ScraperService;

import com.nepse.portfolio.service.HistoryBackfillService;
import com.nepse.portfolio.service.SecurityUniverseService;
import com.nepse.portfolio.service.MarketCalendarService;
import com.nepse.portfolio.dto.market.MarketCalendarDtos.MarketCalendarRequest;
import com.nepse.portfolio.utils.MlClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ScraperService scraperService;
    private final HistoryBackfillService historyBackfillService;
    private final SecurityUniverseService securityUniverseService;
    private final MarketCalendarService marketCalendarService;
    private final MlClient mlClient;

    public AdminController(ScraperService scraperService,
                           HistoryBackfillService historyBackfillService,
                           SecurityUniverseService securityUniverseService,
                           MarketCalendarService marketCalendarService,
                           MlClient mlClient) {
        this.scraperService = scraperService;
        this.historyBackfillService = historyBackfillService;
        this.securityUniverseService = securityUniverseService;
        this.marketCalendarService = marketCalendarService;
        this.mlClient = mlClient;
    }

    @GetMapping("/market/status")
    public Map<String, Object> marketStatus() {
        return scraperService.status(null);
    }

    @PostMapping("/market/sync")
    public Map<String, Object> sync() {
        return scraperService.scrape(true);
    }

    /** Current trading week + session times (editable without a redeploy). */
    @GetMapping("/market/calendar")
    public MarketCalendarService.CalendarView calendar() {
        return marketCalendarService.view();
    }

    /** NEPSE's trading week has changed before; admins update it here. */
    @PutMapping("/market/calendar")
    public MarketCalendarService.CalendarView updateCalendar(
            @RequestBody MarketCalendarRequest request) {
        return marketCalendarService.update(request);
    }

    /**
     * Replaces stored price history with real NEPSE data from the open dataset.
     * This performs one HTTP request per trading day, so allow up to a minute.
     */
    @PostMapping("/market/backfill")
    public HistoryBackfillService.Result backfill(@RequestParam(defaultValue = "260") int days) {
        return historyBackfillService.backfill(days);
    }

    /**
     * Imports every security NEPSE currently lists as active (equities, mutual
     * funds, debentures and promoter shares) and marks missing ones inactive.
     */
    @PostMapping("/market/universe")
    public SecurityUniverseService.Result syncUniverse() {
        return securityUniverseService.sync();
    }

    @GetMapping("/ml/status")
    public Map<String, Object> mlStatus() {
        return mlClient.status().orElse(Map.of("available", false));
    }
}
