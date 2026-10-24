package com.nepse.portfolio.service.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nepse.portfolio.domain.AppSetting;
import com.nepse.portfolio.domain.UserSettings;
import com.nepse.portfolio.exception.ApiException;
import com.nepse.portfolio.repository.AppSettingRepository;
import com.nepse.portfolio.repository.UserRepository;
import com.nepse.portfolio.repository.UserSettingsRepository;
import com.nepse.portfolio.utils.portfolio.TradingRates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The schedule lives in two JSON documents — one admin-global row in
 * app_settings and one optional per-user column — merged field-wise over
 * {@link TradingRates#DEFAULT}. Malformed JSON falls back to the next layer
 * instead of breaking trading.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradingRatesServiceImpl implements TradingRatesService {

    static final String GLOBAL_KEY = "trading-rates";

    private final AppSettingRepository appSettingRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TradingRates effective(Long userId) {
        return TradingRates.merge(TradingRates.merge(TradingRates.DEFAULT, global()), userRates(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public TradingRates userRates(Long userId) {
        return userSettingsRepository.findById(userId)
                .map(this::userOverlay)
                .orElse(new TradingRates(null, null, null, null, null, null, null));
    }

    @Override
    @Transactional
    public TradingRates updateUser(Long userId, TradingRates patch) {
        UserSettings settings = userSettingsRepository.findById(userId)
                .orElseGet(() -> {
                    if (!userRepository.existsById(userId)) {
                        throw ApiException.notFound("User not found");
                    }
                    return userSettingsRepository.save(new UserSettings(userId));
                });
        TradingRates current = userOverlay(settings);
        TradingRates merged = TradingRates.merge(current, patch);
        settings.setTradingRatesJson(write(merged));
        settings.touch();
        userSettingsRepository.save(settings);
        return merged;
    }

    @Override
    @Transactional(readOnly = true)
    public TradingRates global() {
        return appSettingRepository.findByKey(GLOBAL_KEY)
                .map(AppSetting::getValue)
                .map(this::parse)
                .orElse(new TradingRates(null, null, null, null, null, null, null));
    }

    @Override
    @Transactional
    public TradingRates updateGlobal(TradingRates patch) {
        AppSetting row = appSettingRepository.findByKey(GLOBAL_KEY)
                .orElseGet(() -> {
                    AppSetting created = new AppSetting(GLOBAL_KEY, "{}");
                    return appSettingRepository.save(created);
                });
        TradingRates merged = TradingRates.merge(global(), patch);
        row.setValue(write(merged));
        row.touch();
        appSettingRepository.save(row);
        return merged;
    }

    /**
     * The user overlay is the stored JSON column when present; otherwise the
     * legacy flat CGT columns from the settings page still count as a (thin)
     * user-level override so that UI keeps working.
     */
    private TradingRates userOverlay(UserSettings settings) {
        TradingRates json = parse(settings.getTradingRatesJson());
        if (json != null) {
            return json;
        }
        return new TradingRates(null, null, null,
                settings.getCgtShortRate(), settings.getCgtLongRate(), null, null);
    }

    private TradingRates parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, TradingRates.class);
        } catch (Exception e) {
            log.warn("Ignoring unparseable trading-rates JSON: {}", e.getMessage());
            return null;
        }
    }

    private String write(TradingRates rates) {
        try {
            return objectMapper.writeValueAsString(rates);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize trading rates", e);
        }
    }
}
