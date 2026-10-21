package com.nepse.portfolio.domain;

import com.nepse.portfolio.domain.TradeType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades")
@Getter
@Setter
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeType type;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private BigDecimal brokerage = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "trade_date", nullable = false)
    private Instant tradeDate;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Trade() {
    }

    public Trade(Long userId, Long stockId, TradeType type, int quantity, BigDecimal price,
                 BigDecimal brokerage, BigDecimal tax, Instant tradeDate, String notes) {
        this.userId = userId;
        this.stockId = stockId;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.brokerage = brokerage == null ? BigDecimal.ZERO : brokerage;
        this.tax = tax == null ? BigDecimal.ZERO : tax;
        this.tradeDate = tradeDate;
        this.notes = notes;
    }

    public BigDecimal getFees() {
        return brokerage.add(tax);
    }
}
