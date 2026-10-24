package com.nepse.portfolio.controller.settings;

import lombok.RequiredArgsConstructor;
import com.nepse.portfolio.dto.settings.SettingsDtos;
import com.nepse.portfolio.service.settings.SettingsService;
import com.nepse.portfolio.service.portfolio.TradingRatesService;

import com.nepse.portfolio.security.CurrentUser;
import com.nepse.portfolio.utils.portfolio.TradingRates;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final TradingRatesService tradingRatesService;
    private final CurrentUser currentUser;


    @GetMapping
    public SettingsDtos.SettingsDto get() {
        return settingsService.get(currentUser.id());
    }

    @PutMapping
    public SettingsDtos.SettingsDto update(@Valid @RequestBody SettingsDtos.UpdateSettingsRequest request) {
        return settingsService.update(currentUser.id(), request);
    }

    /** The user's own cost/tax schedule (null fields fall back to admin global). */
    @GetMapping("/trading-rates")
    public TradingRates tradingRates() {
        return tradingRatesService.userRates(currentUser.id());
    }

    /** The schedule actually in force after merging admin global over the defaults. */
    @GetMapping("/trading-rates/effective")
    public TradingRates effectiveTradingRates() {
        return tradingRatesService.effective(currentUser.id());
    }

    /** Verified default schedule (no user/admin overrides). */
    @GetMapping("/trading-rates/defaults")
    public TradingRates defaults() {
        return TradingRates.DEFAULT;
    }

    @PutMapping("/trading-rates")
    public TradingRates updateTradingRates(@RequestBody TradingRates patch) {
        return tradingRatesService.updateUser(currentUser.id(), patch);
    }
}
