package com.nepse.portfolio.dto.market;

import java.util.List;

public final class MarketCalendarDtos {

    private MarketCalendarDtos() {
    }

    /**
     * Admin edit for the trading week. Day names are {@link java.time.DayOfWeek}
     * constants; times are {@code HH:mm} in the configured market timezone.
     */
    public record MarketCalendarRequest(
            List<String> tradingDays,
            String openTime,
            String closeTime
    ) {
    }
}
