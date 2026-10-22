package com.nepse.portfolio.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "alert_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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


    public AlertEvent(Long alertId, Long userId, Long stockId, BigDecimal price, String message) {
        this.alertId = alertId;
        this.userId = userId;
        this.stockId = stockId;
        this.price = price;
        this.message = message;
    }
}
