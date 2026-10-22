package com.nepse.portfolio.service.market;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nepse.portfolio.exception.ApiException;
import com.nepse.portfolio.domain.AppSetting;
import com.nepse.portfolio.dto.market.MarketCalendarDtos.MarketCalendarRequest;
import com.nepse.portfolio.repository.AppSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
public class MarketCalendarServiceImpl implements MarketCalendarService {


    private static final String KEY = "market-calendar";

    /** Cached parsed calendar; {@code null} means it must be (re)loaded. */
    private volatile Loaded loaded;

    private final AppSettingRepository repository;
    private final ObjectMapper objectMapper;
    private final List<DayOfWeek> defaultDays;
    private final String defaultOpenTime;
    private final String defaultCloseTime;
    private final ZoneId zone;

    public MarketCalendarServiceImpl(AppSettingRepository repository,
                                     ObjectMapper objectMapper,
                                     @Value("${app.market-calendar.trading-days:MON,TUE,WED,THU,FRI}") String defaultTradingDays,
                                     @Value("${app.market-calendar.market-open:11:00}") String defaultOpenTime,
                                     @Value("${app.market-calendar.market-close:15:00}") String defaultCloseTime,
                                     @Value("${app.scraper.timezone:Asia/Kathmandu}") String timezone) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.defaultDays = parseDays(defaultTradingDays);
        this.defaultOpenTime = defaultOpenTime;
        this.defaultCloseTime = defaultCloseTime;
        this.zone = ZoneId.of(timezone);
    }

    @Override
    public CalendarView view() {
        Loaded current = load();
        return new CalendarView(daysJson(current.days()), current.open().toString(),
                current.close().toString(), current.fromDb() ? "DB" : "DEFAULT");
    }

    @Override
    @Transactional
    public CalendarView update(MarketCalendarRequest request) {
        List<DayOfWeek> days;
        try {
            if (request.tradingDays() == null) {
                throw new IllegalArgumentException("tradingDays is required (list of MON..SUN)");
            }
            days = parseDays(String.join(",", request.tradingDays()));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ApiException.badRequest("tradingDays: " + ex.getMessage());
        }
        if (days.isEmpty()) {
            throw ApiException.badRequest("tradingDays: at least one trading day is required");
        }
        LocalTime open;
        LocalTime close;
        try {
            open = LocalTime.parse(request.openTime() == null ? "" : request.openTime());
            close = LocalTime.parse(request.closeTime() == null ? "" : request.closeTime());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("openTime/closeTime: times must be in HH:mm format");
        }
        if (!close.isAfter(open)) {
            throw ApiException.badRequest("closeTime: must be after the open time");
        }

        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode array = node.putArray("tradingDays");
        for (DayOfWeek day : days) {
            array.add(day.name());
        }
        node.put("openTime", open.toString());
        node.put("closeTime", close.toString());

        AppSetting row = repository.findByKey(KEY).orElseGet(() -> new AppSetting(KEY, ""));
        String json;
        try {
            json = objectMapper.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise market calendar", ex);
        }
        row.setValue(json);
        row.touch();
        repository.save(row);
        loaded = null;
        log.info("Market calendar updated: {} {}-{}", days, open, close);
        return view();
    }

    @Override
    public boolean isMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        Loaded current = load();
        if (!current.days().contains(now.getDayOfWeek())) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(current.open()) && !time.isAfter(current.close());
    }

    @Override
    public boolean isTradingDay(LocalDate date) {
        return load().days().contains(date.getDayOfWeek());
    }

    @Override
    public Set<DayOfWeek> tradingDays() {
        return EnumSet.copyOf(load().days());
    }

    private Loaded load() {
        Loaded current = loaded;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = loaded;
            if (current != null) {
                return current;
            }
            current = repository.findByKey(KEY)
                    .map(this::parseRow)
                    .orElseGet(this::defaults);
            loaded = current;
            return current;
        }
    }

    private Loaded parseRow(AppSetting row) {
        try {
            JsonNode node = objectMapper.readTree(row.getValue());
            List<DayOfWeek> days = parseDays(textArray(node.path("tradingDays")));
            LocalTime open = LocalTime.parse(node.path("openTime").asText("11:00"));
            LocalTime close = LocalTime.parse(node.path("closeTime").asText("15:00"));
            if (days.isEmpty() || !close.isAfter(open)) {
                throw new IllegalArgumentException("Invalid stored calendar");
            }
            return new Loaded(days, open, close, true);
        } catch (Exception ex) {
            log.warn("Stored market calendar is unusable, falling back to defaults: {}",
                    ex.getMessage());
            return defaults();
        }
    }

    private Loaded defaults() {
        return new Loaded(defaultDays,
                LocalTime.parse(defaultOpenTime),
                LocalTime.parse(defaultCloseTime),
                false);
    }

    private List<DayOfWeek> parseDays(String joined) {
        List<DayOfWeek> days = new ArrayList<>();
        for (String part : joined.split(",")) {
            String name = part.trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            days.add(resolveDay(name)); // throws for junk
        }
        return days;
    }

    /** Accepts both Java full names and the common 3-letter abbreviations. */
    private static DayOfWeek resolveDay(String name) {
        if (name.length() == 3) {
            return switch (name) {
                case "MON" -> DayOfWeek.MONDAY;
                case "TUE" -> DayOfWeek.TUESDAY;
                case "WED" -> DayOfWeek.WEDNESDAY;
                case "THU" -> DayOfWeek.THURSDAY;
                case "FRI" -> DayOfWeek.FRIDAY;
                case "SAT" -> DayOfWeek.SATURDAY;
                case "SUN" -> DayOfWeek.SUNDAY;
                default -> throw new IllegalArgumentException(name + " is not a day name");
            };
        }
        return DayOfWeek.valueOf(name);
    }

    private String textArray(JsonNode node) {
        List<String> names = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : (ArrayNode) node) {
                names.add(item.asText());
            }
        }
        return String.join(",", names);
    }

    private List<String> daysJson(List<DayOfWeek> days) {
        return days.stream().map(Enum::name).toList();
    }

    private record Loaded(List<DayOfWeek> days, LocalTime open, LocalTime close, boolean fromDb) {
    }
}
