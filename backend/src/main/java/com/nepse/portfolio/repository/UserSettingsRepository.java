package com.nepse.portfolio.repository;

import com.nepse.portfolio.domain.UserSettings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}
