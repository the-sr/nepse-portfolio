package com.nepse.portfolio.dto.analysis;


import java.math.BigDecimal;

public record Indicators(
        BigDecimal sma20,
        BigDecimal sma50,
        BigDecimal rsi14,
        BigDecimal return1d,
        BigDecimal return7d,
        BigDecimal return30d,
        BigDecimal volatility,
        BigDecimal volumeRatio,
        String trend,
        BigDecimal support,
        BigDecimal resistance,
        BigDecimal high52Week,
        BigDecimal low52Week,
        BigDecimal averageVolume20,
        int dataPoints
) {
}
