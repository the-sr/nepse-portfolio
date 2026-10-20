package com.nepse.portfolio.user;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_settings")
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

    public Long getUserId() {
        return userId;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public BigDecimal getLossThresholdMinor() {
        return lossThresholdMinor;
    }

    public void setLossThresholdMinor(BigDecimal value) {
        this.lossThresholdMinor = value;
    }

    public BigDecimal getLossThresholdMajor() {
        return lossThresholdMajor;
    }

    public void setLossThresholdMajor(BigDecimal value) {
        this.lossThresholdMajor = value;
    }

    public BigDecimal getLossThresholdAggressive() {
        return lossThresholdAggressive;
    }

    public void setLossThresholdAggressive(BigDecimal value) {
        this.lossThresholdAggressive = value;
    }

    public BigDecimal getBaseBuyAmount() {
        return baseBuyAmount;
    }

    public void setBaseBuyAmount(BigDecimal value) {
        this.baseBuyAmount = value;
    }

    public BigDecimal getMaxPositionPercent() {
        return maxPositionPercent;
    }

    public void setMaxPositionPercent(BigDecimal value) {
        this.maxPositionPercent = value;
    }

    public BigDecimal getMaxCapitalPerStock() {
        return maxCapitalPerStock;
    }

    public void setMaxCapitalPerStock(BigDecimal value) {
        this.maxCapitalPerStock = value;
    }

    public BigDecimal getCgtShortRate() {
        return cgtShortRate;
    }

    public void setCgtShortRate(BigDecimal value) {
        this.cgtShortRate = value;
    }

    public BigDecimal getCgtLongRate() {
        return cgtLongRate;
    }

    public void setCgtLongRate(BigDecimal value) {
        this.cgtLongRate = value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
