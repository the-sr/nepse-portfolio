package com.nepse.portfolio.user;

import com.nepse.portfolio.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final CurrentUser currentUser;

    public SettingsController(SettingsService settingsService, CurrentUser currentUser) {
        this.settingsService = settingsService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public SettingsDtos.SettingsDto get() {
        return settingsService.get(currentUser.id());
    }

    @PutMapping
    public SettingsDtos.SettingsDto update(@Valid @RequestBody SettingsDtos.UpdateSettingsRequest request) {
        return settingsService.update(currentUser.id(), request);
    }
}
