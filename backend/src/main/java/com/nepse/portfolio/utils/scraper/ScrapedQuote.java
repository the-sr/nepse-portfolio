package com.nepse.portfolio.utils.scraper;


import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single market quote, normalised across providers.
 *
 * @param asOf trading date reported by the provider, or {@code null} when the
 *             provider does not expose one (the current date is assumed).
 */
public record ScrapedQuote(
        String symbol,
        BigDecimal ltp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal prevClose,
        BigDecimal changePercent,
        long volume,
        BigDecimal turnover,
        LocalDate asOf
) {
}
