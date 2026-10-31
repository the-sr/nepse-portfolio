package com.nepse.portfolio.repository;

import com.nepse.portfolio.model.UserSettings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}
