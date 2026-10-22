package com.nepse.portfolio.service.market;

import com.nepse.portfolio.repository.PriceHistoryRepository;
import com.nepse.portfolio.service.market.MarketCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoryBackfillGapTest {

    private PriceHistoryRepository historyRepository;
    private HistoryBackfillServiceImpl service;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        historyRepository = mock(PriceHistoryRepository.class);
        MarketCalendarService calendar = mock(MarketCalendarService.class);
        // calendar.isTradingDay depends on the real date, so tests only rely on
        // scenarios that hold regardless of the day of the week.
        when(calendar.isTradingDay(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        service = new HistoryBackfillServiceImpl(null, null, null, calendar,
                historyRepository, null, 6, "Asia/Kathmandu");
    }

    @Test
    void emptyTableNeedsRepair() {
        when(historyRepository.findLatestDateOverall()).thenReturn(Optional.empty());
        assertThat(service.needsRepair()).isTrue();
    }

    @Test
    void tableCurrentThroughTodayDoesNotNeedRepair() {
        when(historyRepository.findLatestDateOverall()).thenReturn(Optional.of(today));
        assertThat(service.needsRepair()).isFalse();
        assertThat(service.repairIfStale(260)).isEmpty();
    }

    @Test
    void staleNewestBarMeansRepairNeeded() {
        when(historyRepository.findLatestDateOverall()).thenReturn(Optional.of(today.minusDays(30)));
        assertThat(service.needsRepair()).isTrue();
    }

    @Test
    void staleNewestBarMeansRepairNeededMayTriggerBackfill() {
        when(historyRepository.findLatestDateOverall()).thenReturn(Optional.of(today.minusDays(30)));
        // repairIfStale proceeds to a network backfill; a failure there is fine,
        // it must simply not silently return "nothing to do".
        try {
            Optional<?> result = service.repairIfStale(5);
            assertThat(result).isPresent();
        } catch (Exception expected) {
            // the test suite has no network/db access: any real backfill attempt
            // failing loudly is correct behaviour - it must not silently no-op
            assertThat(String.valueOf(expected.getMessage())).isNotBlank();
        }
    }
}
