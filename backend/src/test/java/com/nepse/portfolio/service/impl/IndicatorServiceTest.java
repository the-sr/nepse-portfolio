package com.nepse.portfolio.service.impl;

import com.nepse.portfolio.dto.analysis.Indicators;
import com.nepse.portfolio.service.IndicatorService;

import com.nepse.portfolio.domain.PriceHistory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndicatorServiceTest {

    private final IndicatorService service = new IndicatorServiceImpl();

    private List<PriceHistory> series(double start, double dailyGain, int count) {
        List<PriceHistory> points = new ArrayList<>();
        double price = start;
        LocalDate date = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < count; i++) {
            BigDecimal close = BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP);
            points.add(new PriceHistory(1L, date.plusDays(i),
                    close, close.add(BigDecimal.ONE), close.subtract(BigDecimal.ONE), close, 1000 + i));
            price *= 1 + dailyGain;
        }
        return points;
    }

    @Test
    void detectsAnUptrend() {
        Indicators indicators = service.compute(series(100, 0.01, 120));

        assertThat(indicators.trend()).isEqualTo("BULLISH");
        assertThat(indicators.sma20()).isNotNull();
        assertThat(indicators.sma50()).isNotNull();
        assertThat(indicators.rsi14()).isGreaterThan(new BigDecimal("50"));
        assertThat(indicators.return30d()).isPositive();
        assertThat(indicators.support()).isLessThanOrEqualTo(indicators.resistance());
        assertThat(indicators.dataPoints()).isEqualTo(120);
    }

    @Test
    void detectsADowntrend() {
        Indicators indicators = service.compute(series(500, -0.008, 120));

        assertThat(indicators.trend()).isEqualTo("BEARISH");
        assertThat(indicators.rsi14()).isLessThan(new BigDecimal("50"));
        assertThat(indicators.return30d()).isNegative();
    }

    @Test
    void handlesShortHistoryWithoutExploding() {
        Indicators indicators = service.compute(series(100, 0.005, 10));

        assertThat(indicators.dataPoints()).isEqualTo(10);
        assertThat(indicators.sma20()).isNull();
        assertThat(indicators.rsi14()).isNull();
        assertThat(indicators.trend()).isIn("BULLISH", "BEARISH", "SIDEWAYS");
    }
}
