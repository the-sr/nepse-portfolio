package com.nepse.portfolio.controller;

import com.nepse.portfolio.service.market.ScraperService;

import com.nepse.portfolio.service.market.MarketCalendarService;
import com.nepse.portfolio.service.portfolio.TradingRatesService;
import com.nepse.portfolio.dto.MarketCalendarDtos.MarketCalendarRequest;
import com.nepse.portfolio.utils.ml.MlClient;
import com.nepse.portfolio.utils.portfolio.TradingRates;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ScraperService scraperService;
    private final MarketCalendarService marketCalendarService;
    private final TradingRatesService tradingRatesService;
    private final MlClient mlClient;

    /** Admin-global commission/SEBON/DP/CGT schedule every user inherits. */
    @GetMapping("/trading-rates")
    public TradingRates tradingRates() {
        return tradingRatesService.global();
    }

    @PutMapping("/trading-rates")
    public TradingRates updateTradingRates(@RequestBody TradingRates patch) {
        return tradingRatesService.updateGlobal(patch);
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

    @GetMapping("/ml/status")
    public Map<String, Object> mlStatus() {
        return mlClient.status().orElse(Map.of("available", false));
    }
}
