package com.nepse.portfolio.alert;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "alert_events")
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AlertEvent() {
    }

    public AlertEvent(Long alertId, Long userId, Long stockId, BigDecimal price, String message) {
        this.alertId = alertId;
        this.userId = userId;
        this.stockId = stockId;
        this.price = price;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public Long getAlertId() {
        return alertId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getStockId() {
        return stockId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
