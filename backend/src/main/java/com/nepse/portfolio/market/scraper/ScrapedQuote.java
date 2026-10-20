package com.nepse.portfolio.market.scraper;

import java.math.BigDecimal;

public record ScrapedQuote(
        String symbol,
        BigDecimal ltp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal prevClose,
        BigDecimal changePercent,
        long volume,
        BigDecimal turnover
) {
}
