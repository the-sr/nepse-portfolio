package com.nepse.portfolio.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String theme = "dark";

    @Column(name = "loss_threshold_minor", nullable = false)
    private BigDecimal lossThresholdMinor = new BigDecimal("5");

    @Column(name = "loss_threshold_major", nullable = false)
    private BigDecimal lossThresholdMajor = new BigDecimal("10");

    @Column(name = "loss_threshold_aggressive", nullable = false)
    private BigDecimal lossThresholdAggressive = new BigDecimal("15");

    @Column(name = "base_buy_amount", nullable = false)
    private BigDecimal baseBuyAmount = new BigDecimal("10000");

    @Column(name = "max_position_percent", nullable = false)
    private BigDecimal maxPositionPercent = new BigDecimal("25");

    @Column(name = "max_capital_per_stock", nullable = false)
    private BigDecimal maxCapitalPerStock = new BigDecimal("100000");

    @Column(name = "cgt_short_rate", nullable = false)
    private BigDecimal cgtShortRate = new BigDecimal("7.5");

    @Column(name = "cgt_long_rate", nullable = false)
    private BigDecimal cgtLongRate = new BigDecimal("5.0");

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserSettings() {
    }

    public UserSettings(Long userId) {
        this.userId = userId;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
