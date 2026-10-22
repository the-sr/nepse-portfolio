package com.nepse.portfolio.controller.settings;

import lombok.RequiredArgsConstructor;
import com.nepse.portfolio.dto.settings.SettingsDtos;
import com.nepse.portfolio.service.settings.SettingsService;

import com.nepse.portfolio.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final CurrentUser currentUser;


    @GetMapping
    public SettingsDtos.SettingsDto get() {
        return settingsService.get(currentUser.id());
    }

    @PutMapping
    public SettingsDtos.SettingsDto update(@Valid @RequestBody SettingsDtos.UpdateSettingsRequest request) {
        return settingsService.update(currentUser.id(), request);
    }
}
