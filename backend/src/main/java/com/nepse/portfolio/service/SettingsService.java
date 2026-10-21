package com.nepse.portfolio.service;

import com.nepse.portfolio.domain.UserSettings;
import com.nepse.portfolio.dto.settings.SettingsDtos.SettingsDto;
import com.nepse.portfolio.dto.settings.SettingsDtos.UpdateSettingsRequest;

public interface SettingsService {
    SettingsDto get(Long userId);
    SettingsDto update(Long userId, UpdateSettingsRequest request);
    UserSettings loadOrCreate(Long userId);
}
