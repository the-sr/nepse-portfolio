package com.nepse.portfolio.domain;

import com.nepse.portfolio.domain.AlertType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "alerts")
@Getter
@Setter
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

    public void markTriggered() {
        this.triggered = true;
        this.lastTriggeredAt = Instant.now();
    }

    public void rearm() {
        this.triggered = false;
    }
}
