package com.nepse.portfolio.market;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    private String sector;

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

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getSector() {
        return sector;
    }

    public BigDecimal getLtp() {
        return ltp;
    }

    public void setLtp(BigDecimal ltp) {
        this.ltp = ltp;
    }

    public BigDecimal getPrevClose() {
        return prevClose;
    }

    public void setPrevClose(BigDecimal prevClose) {
        this.prevClose = prevClose;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public long getVolume() {
        return volume;
    }

    public void setVolume(long volume) {
        this.volume = volume;
    }

    public BigDecimal getTurnover() {
        return turnover;
    }

    public void setTurnover(BigDecimal turnover) {
        this.turnover = turnover;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public BigDecimal getPeRatio() {
        return peRatio;
    }

    public BigDecimal getEps() {
        return eps;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void touch() {
        this.lastUpdated = Instant.now();
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
}
