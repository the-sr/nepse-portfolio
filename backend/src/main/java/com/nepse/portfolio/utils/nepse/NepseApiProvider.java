package com.nepse.portfolio.utils.nepse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nepse.portfolio.utils.nepse.NepseApiClient;
import com.nepse.portfolio.utils.nepse.NepseLiveQuote;

import com.nepse.portfolio.utils.scraper.MarketDataProvider;
import com.nepse.portfolio.utils.scraper.ScrapedQuote;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Primary market data provider: NEPSE's own API.
 *
 * <p>One request returns every listed security with open/high/low/LTP, previous
 * close, volume and turnover - no HTML parsing and no per-symbol calls.</p>
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.nepse.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class NepseApiProvider implements MarketDataProvider {

    public static final String PROVIDER_NAME = "nepse";


    private final NepseApiClient apiClient;


    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public String description() {
        return "NEPSE official live market API";
    }

    @Override
    public List<ScrapedQuote> fetch() {
        List<NepseLiveQuote> quotes = apiClient.fetchLiveMarket();
        List<ScrapedQuote> mapped = quotes.stream()
                .map(NepseApiProvider::toQuote)
                .flatMap(Optional::stream)
                .toList();
        log.debug("Mapped {} of {} NEPSE quotes", mapped.size(), quotes.size());
        return mapped;
    }

    static Optional<ScrapedQuote> toQuote(NepseLiveQuote quote) {
        if (quote == null || quote.symbol() == null || quote.symbol().isBlank()) {
            return Optional.empty();
        }
        BigDecimal ltp = quote.lastTradedPrice();
        if (ltp == null || ltp.signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal open = positiveOr(quote.openPrice(), ltp);
        BigDecimal high = positiveOr(quote.highPrice(), ltp.max(open));
        BigDecimal low = positiveOr(quote.lowPrice(), ltp.min(open));
        BigDecimal prevClose = positiveOr(quote.previousClose(), ltp);

        BigDecimal changePercent = quote.percentageChange();
        if (changePercent == null) {
            changePercent = prevClose.signum() == 0
                    ? BigDecimal.ZERO
                    : ltp.subtract(prevClose)
                    .divide(prevClose, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        long volume = quote.totalTradeQuantity() == null ? 0L : Math.max(0L, quote.totalTradeQuantity());
        BigDecimal turnover = quote.totalTradeValue() == null ? BigDecimal.ZERO : quote.totalTradeValue();

        return Optional.of(new ScrapedQuote(
                quote.symbol().trim().toUpperCase(java.util.Locale.ROOT),
                ltp,
                open,
                high,
                low,
                prevClose,
                changePercent,
                volume,
                turnover,
                parseDate(quote.lastUpdatedDateTime())
        ));
    }

    private static BigDecimal positiveOr(BigDecimal value, BigDecimal fallback) {
        return value != null && value.signum() > 0 ? value : fallback;
    }

    /**
     * NEPSE reports timestamps such as {@code 2026-10-16 15:00:00.000876}.
     * Only the date matters for daily bars.
     */
    static LocalDate parseDate(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(timestamp.substring(0, 10));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
