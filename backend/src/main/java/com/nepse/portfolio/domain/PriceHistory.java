package com.nepse.portfolio.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "price_history")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", nullable = false)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false)
    private BigDecimal closePrice;

    @Column(nullable = false)
    private long volume;


    public PriceHistory(Long stockId, LocalDate tradeDate, BigDecimal openPrice, BigDecimal highPrice,
                        BigDecimal lowPrice, BigDecimal closePrice, long volume) {
        this.stockId = stockId;
        this.tradeDate = tradeDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }
}
