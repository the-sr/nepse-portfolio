package com.nepse.portfolio.service.market;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HistoryBackfillService {

    record Result(
            int daysRequested,
            int filesFetched,
            int filesMissing,
            List<String> missingDates,
            int barsWritten,
            int symbolsMatched,
            int unknownSymbols,
            List<String> unknownSymbolSamples,
            int barsReplaced,
            int pricesBackfilled,
            LocalDate from,
            LocalDate to
    ) {
    }

    Result backfill(int days);

    /**
     * True when the newest stored bar is older than the calendar's most recent
     * trading day (or the table is empty), i.e. history has a gap.
     */
    boolean needsRepair();

    /**
     * Automatic maintenance used on startup and nightly: checks for gaps and
     * backfills only the missing window (full window on an empty table).
     * Returns empty when history is already current.
     */
    Optional<Result> repairIfStale(int windowDays);
}
