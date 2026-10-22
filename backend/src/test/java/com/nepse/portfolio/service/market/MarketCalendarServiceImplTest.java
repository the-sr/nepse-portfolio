package com.nepse.portfolio.service.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nepse.portfolio.domain.AppSetting;
import com.nepse.portfolio.dto.market.MarketCalendarDtos.MarketCalendarRequest;
import com.nepse.portfolio.repository.AppSettingRepository;
import com.nepse.portfolio.service.market.MarketCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketCalendarServiceImplTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private AppSettingRepository repository;
    private MarketCalendarServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(AppSettingRepository.class);
        AtomicReference<AppSetting> savedRow = new AtomicReference<>();
        when(repository.findByKey("market-calendar"))
                .thenAnswer(inv -> Optional.ofNullable(savedRow.get()));
        when(repository.save(any(AppSetting.class))).thenAnswer(inv -> {
            savedRow.set((AppSetting) inv.getArgument(0));
            return savedRow.get();
        });
        service = new MarketCalendarServiceImpl(repository, mapper,
                "MON,TUE,WED,THU,FRI", "11:00", "15:00", "Asia/Kathmandu");
    }

    @Test
    void fallsBackToConfiguredDefaultsWhenNoRowExists() {
        MarketCalendarService.CalendarView view = service.view();

        assertThat(view.tradingDays()).containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");
        assertThat(view.openTime()).isEqualTo("11:00");
        assertThat(view.closeTime()).isEqualTo("15:00");
        assertThat(view.source()).isEqualTo("DEFAULT");
    }

    @Test
    void readsAdminEditedWeekFromTheDatabase() {
        AppSetting row = new AppSetting("market-calendar",
                "{\"tradingDays\":[\"SUN\",\"MON\",\"TUE\",\"WED\",\"THU\"],\"openTime\":\"10:00\",\"closeTime\":\"14:00\"}");
        when(repository.findByKey("market-calendar")).thenReturn(Optional.of(row));

        MarketCalendarService.CalendarView view = service.view();

        assertThat(view.tradingDays()).containsExactly("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY");
        assertThat(view.source()).isEqualTo("DB");
        assertThat(service.isTradingDay(LocalDate.of(2025, 3, 9))).isTrue();   // Sunday
        assertThat(service.isTradingDay(LocalDate.of(2025, 3, 7))).isFalse(); // Friday
    }

    @Test
    void unusableStoredRowFallsBackToDefaults() {
        AppSetting row = new AppSetting("market-calendar", "not json at all");
        when(repository.findByKey("market-calendar")).thenReturn(Optional.of(row));

        MarketCalendarService.CalendarView view = service.view();

        assertThat(view.source()).isEqualTo("DEFAULT");
        assertThat(service.isTradingDay(LocalDate.of(2025, 3, 7))).isTrue(); // Friday, Mon-Fri default
    }

    @Test
    void updateValidatesAndPersists() {
        MarketCalendarService.CalendarView updated = service.update(
                new MarketCalendarRequest(List.of("mon", "tue", "wed", "thu", "fri"),
                        "11:15", "14:45"));

        assertThat(updated.tradingDays()).containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");
        assertThat(updated.openTime()).isEqualTo("11:15");
        assertThat(updated.closeTime()).isEqualTo("14:45");
        assertThat(updated.source()).isEqualTo("DB");

        // a bad day name or reversed times must be rejected
        assertThatThrownBy(() -> service.update(
                new MarketCalendarRequest(List.of("FUNDAY"), "11:00", "15:00")))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("tradingDays");
        assertThatThrownBy(() -> service.update(
                new MarketCalendarRequest(List.of("MON"), "16:00", "10:00")))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("after the open time");
        assertThatThrownBy(() -> service.update(
                new MarketCalendarRequest(List.of(), "11:00", "15:00")))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void marketSessionCheckRespectsTradingDays() {
        assertThat(service.tradingDays()).contains(java.time.DayOfWeek.MONDAY);
        assertThat(service.tradingDays()).doesNotContain(java.time.DayOfWeek.SUNDAY);
        assertThat(service.isTradingDay(LocalDate.of(2026, 10, 19))).isTrue();  // Monday
        assertThat(service.isTradingDay(LocalDate.of(2026, 10, 18))).isFalse(); // Sunday
    }

    @Test
    void parsesStoredJsonWithDefaultsForMissingFields() {
        AppSetting row = new AppSetting("market-calendar", "{\"tradingDays\":[\"MON\"]}");
        when(repository.findByKey("market-calendar")).thenReturn(Optional.of(row));

        assertThat(service.view().openTime()).isEqualTo("11:00");
        assertThat(service.view().closeTime()).isEqualTo("15:00");
        assertThat(service.view().tradingDays()).containsExactly("MONDAY");
    }
}
