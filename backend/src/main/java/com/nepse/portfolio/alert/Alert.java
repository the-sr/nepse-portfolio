package com.nepse.portfolio.alert;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    @Column(nullable = false)
    private BigDecimal threshold;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean triggered = false;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Alert() {
    }

    public Alert(Long userId, Long stockId, AlertType type, BigDecimal threshold) {
        this.userId = userId;
        this.stockId = stockId;
        this.type = type;
        this.threshold = threshold;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getStockId() {
        return stockId;
    }

    public AlertType getType() {
        return type;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markTriggered() {
        this.triggered = true;
        this.lastTriggeredAt = Instant.now();
    }

    public void rearm() {
        this.triggered = false;
    }
}
