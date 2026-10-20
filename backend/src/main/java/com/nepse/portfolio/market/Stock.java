package com.nepse.portfolio.market;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stocks")
@Getter
@Setter
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    private String sector;

    @Column(name = "instrument_type", nullable = false)
    private String instrumentType = "Equity";

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private BigDecimal ltp = BigDecimal.ZERO;

    @Column(name = "prev_close", nullable = false)
    private BigDecimal prevClose = BigDecimal.ZERO;

    @Column(name = "open_price", nullable = false)
    private BigDecimal openPrice = BigDecimal.ZERO;

    @Column(name = "high_price", nullable = false)
    private BigDecimal highPrice = BigDecimal.ZERO;

    @Column(name = "low_price", nullable = false)
    private BigDecimal lowPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private long volume = 0;

    @Column(nullable = false)
    private BigDecimal turnover = BigDecimal.ZERO;

    @Column(name = "market_cap")
    private BigDecimal marketCap;

    @Column(name = "pe_ratio")
    private BigDecimal peRatio;

    private BigDecimal eps;

    @Column(nullable = false)
    private String source = "SEEDED";

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated = Instant.now();

    protected Stock() {
    }

    public Stock(String symbol, String companyName, String sector, String instrumentType) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.sector = sector;
        this.instrumentType = instrumentType;
        this.source = "NEPSE";
    }

    public BigDecimal getChange() {
        return ltp.subtract(prevClose);
    }

    public BigDecimal getChangePercent() {
        if (prevClose == null || prevClose.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return ltp.subtract(prevClose)
                .multiply(BigDecimal.valueOf(100))
                .divide(prevClose, 2, java.math.RoundingMode.HALF_UP);
    }

    public void touch() {
        this.lastUpdated = Instant.now();
    }
}
