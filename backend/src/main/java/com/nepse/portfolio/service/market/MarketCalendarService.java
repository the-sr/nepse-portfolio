package com.nepse.portfolio.service.market;

import com.nepse.portfolio.dto.market.MarketCalendarDtos.MarketCalendarRequest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * NEPSE's trading week is configuration, not code. The exchange already moved
 * from Sunday-Thursday to Monday-Friday and can move again, so the trading
 * days and session times live in an admin-editable database row and the live
 * market status is verified against NEPSE's own API before this calendar is
 * used as a fallback.
 */
public interface MarketCalendarService {

    /** Current calendar together with where it came from: "DB" or "DEFAULT". */
    record CalendarView(
            List<String> tradingDays,
            String openTime,
            String closeTime,
            String source
    ) {
    }

    CalendarView view();

    /** Validates and persists a new calendar; throws on invalid input. */
    CalendarView update(MarketCalendarRequest request);

    /** Is the session running right now, according to the stored calendar? */
    boolean isMarketOpen();

    /** Would this date be a trading day, ignoring public holidays? */
    boolean isTradingDay(LocalDate date);

    Set<DayOfWeek> tradingDays();
}
