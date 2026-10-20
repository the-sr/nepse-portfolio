package com.nepse.portfolio.user;

import com.nepse.portfolio.common.ApiException;
import com.nepse.portfolio.user.SettingsDtos.SettingsDto;
import com.nepse.portfolio.user.SettingsDtos.UpdateSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final UserSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    public SettingsService(UserSettingsRepository settingsRepository, UserRepository userRepository) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SettingsDto get(Long userId) {
        return toDto(loadOrCreate(userId));
    }

    @Transactional
    public SettingsDto update(Long userId, UpdateSettingsRequest request) {
        UserSettings settings = loadOrCreate(userId);
        if (request.theme() != null) {
            settings.setTheme(request.theme());
        }
        if (request.lossThresholdMinor() != null) {
            settings.setLossThresholdMinor(request.lossThresholdMinor());
        }
        if (request.lossThresholdMajor() != null) {
            settings.setLossThresholdMajor(request.lossThresholdMajor());
        }
        if (request.lossThresholdAggressive() != null) {
            settings.setLossThresholdAggressive(request.lossThresholdAggressive());
        }
        if (request.baseBuyAmount() != null) {
            settings.setBaseBuyAmount(request.baseBuyAmount());
        }
        if (request.maxPositionPercent() != null) {
            settings.setMaxPositionPercent(request.maxPositionPercent());
        }
        if (request.maxCapitalPerStock() != null) {
            settings.setMaxCapitalPerStock(request.maxCapitalPerStock());
        }
        if (request.cgtShortRate() != null) {
            settings.setCgtShortRate(request.cgtShortRate());
        }
        if (request.cgtLongRate() != null) {
            settings.setCgtLongRate(request.cgtLongRate());
        }
        settings.touch();
        return toDto(settingsRepository.save(settings));
    }

    @Transactional
    public UserSettings loadOrCreate(Long userId) {
        return settingsRepository.findById(userId)
                .orElseGet(() -> {
                    if (!userRepository.existsById(userId)) {
                        throw ApiException.notFound("User not found");
                    }
                    return settingsRepository.save(new UserSettings(userId));
                });
    }

    private SettingsDto toDto(UserSettings s) {
        return new SettingsDto(
                s.getTheme(),
                s.getLossThresholdMinor(),
                s.getLossThresholdMajor(),
                s.getLossThresholdAggressive(),
                s.getBaseBuyAmount(),
                s.getMaxPositionPercent(),
                s.getMaxCapitalPerStock(),
                s.getCgtShortRate(),
                s.getCgtLongRate()
        );
    }
}
