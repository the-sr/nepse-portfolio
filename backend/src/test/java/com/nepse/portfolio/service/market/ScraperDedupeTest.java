package com.nepse.portfolio.service.market;

import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.utils.scraper.ScrapedQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperDedupeTest {

    private Stock stock() {
        Stock stock = new Stock("NICA", "NIC Asia Bank", "Commercial Bank", "Equity");
        stock.setLtp(new BigDecimal("1000.00"));
        stock.setOpenPrice(new BigDecimal("995.00"));
        stock.setHighPrice(new BigDecimal("1005.00"));
        stock.setLowPrice(new BigDecimal("990.00"));
        stock.setPrevClose(new BigDecimal("998.00"));
        stock.setVolume(150000L);
        stock.setTurnover(new BigDecimal("12000000.00"));
        return stock;
    }

    private ScrapedQuote quote(BigDecimal ltp, long volume) {
        return new ScrapedQuote("NICA", ltp, new BigDecimal("995.00"),
                new BigDecimal("1005.00"), new BigDecimal("990.00"),
                new BigDecimal("998.00"), null, volume,
                new BigDecimal("12000000.00"), LocalDate.of(2026, 10, 20));
    }

    @Test
    void identicalQuoteIsNotChanged() {
        assertThat(ScraperServiceImpl.hasChanged(stock(), quote(
                new BigDecimal("1000.00"), 150000L))).isFalse();
        // scale differences must not count as changes (1000.0 vs 1000.00)
        assertThat(ScraperServiceImpl.hasChanged(stock(), quote(
                new BigDecimal("1000.0"), 150000L))).isFalse();
    }

    @Test
    void anyScalarChangeIsDetected() {
        assertThat(ScraperServiceImpl.hasChanged(stock(), quote(
                new BigDecimal("1001.00"), 150000L))).isTrue();   // ltp moved
        assertThat(ScraperServiceImpl.hasChanged(stock(), quote(
                new BigDecimal("1000.00"), 150001L))).isTrue();   // volume ticked

        Stock quiet = stock();
        quiet.setOpenPrice(new BigDecimal("0.00"));
        assertThat(ScraperServiceImpl.hasChanged(quiet, quote(
                new BigDecimal("1000.00"), 150000L))).isTrue();   // open differs
    }

    @Test
    void nullTurnoverOnQuoteIsNavigable() {
        ScrapedQuote noTurnover = new ScrapedQuote("NICA", new BigDecimal("1000.00"),
                new BigDecimal("995.00"), new BigDecimal("1005.00"),
                new BigDecimal("990.00"), new BigDecimal("998.00"), null, 150000L,
                null, LocalDate.now());
        assertThat(ScraperServiceImpl.hasChanged(stock(), noTurnover)).isTrue();
    }
}
