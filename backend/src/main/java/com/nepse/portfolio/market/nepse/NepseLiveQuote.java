package com.nepse.portfolio.market.nepse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * One row of NEPSE's {@code /api/nots/lives-market} response. Unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NepseLiveQuote(
        String symbol,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal lastTradedPrice,
        BigDecimal previousClose,
        BigDecimal percentageChange,
        Long totalTradeQuantity,
        BigDecimal totalTradeValue,
        String lastUpdatedDateTime
) {
}
