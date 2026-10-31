package com.nepse.portfolio.service.settings;

import com.nepse.portfolio.model.UserSettings;
import com.nepse.portfolio.dto.SettingsDtos.SettingsDto;
import com.nepse.portfolio.dto.SettingsDtos.UpdateSettingsRequest;

public interface SettingsService {
    SettingsDto get(Long userId);
    SettingsDto update(Long userId, UpdateSettingsRequest request);
    UserSettings loadOrCreate(Long userId);
}
