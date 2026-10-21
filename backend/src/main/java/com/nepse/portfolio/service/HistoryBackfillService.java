package com.nepse.portfolio.service;

import java.time.LocalDate;
import java.util.List;

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
}
