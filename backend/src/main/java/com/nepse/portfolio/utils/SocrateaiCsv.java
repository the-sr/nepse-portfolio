package com.nepse.portfolio.utils;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses a socrateai "nepse-open-data" daily OHLC file.
 *
 * <pre>
 * date,close,open,high,low,volume,symbol
 * 2026-10-16,850.0,850.0,855.0,850.0,892,ACLBSL
 * </pre>
 */
public final class SocrateaiCsv {

    private SocrateaiCsv() {
    }

    public record Row(LocalDate date, String symbol, BigDecimal open, BigDecimal high,
                      BigDecimal low, BigDecimal close, long volume) {
    }

    public static List<Row> parse(String csv) {
        List<Row> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        String[] lines = csv.split("\\R");
        if (lines.length < 2) {
            return rows;
        }

        Map<String, Integer> columns = header(lines[0]);
        Integer dateIdx = columns.get("date");
        Integer symbolIdx = columns.get("symbol");
        if (dateIdx == null || symbolIdx == null) {
            return rows;
        }

        // A few dataset files repeat every row; keep the first occurrence.
        Set<String> seen = new HashSet<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cells = line.split(",", -1);
            try {
                LocalDate date = LocalDate.parse(cell(cells, dateIdx));
                String symbol = cell(cells, symbolIdx).trim().toUpperCase(Locale.ROOT);
                if (symbol.isEmpty()) {
                    continue;
                }
                BigDecimal close = decimal(cell(cells, columns.get("close")));
                BigDecimal open = decimal(cell(cells, columns.get("open")));
                BigDecimal high = decimal(cell(cells, columns.get("high")));
                BigDecimal low = decimal(cell(cells, columns.get("low")));
                long volume = (long) Double.parseDouble(orZero(cell(cells, columns.get("volume"))));

                if (close == null || close.signum() <= 0) {
                    continue;
                }
                if (!seen.add(date + "|" + symbol)) {
                    continue;
                }
                rows.add(new Row(date, symbol,
                        positiveOr(open, close),
                        positiveOr(high, close),
                        positiveOr(low, close),
                        close,
                        Math.max(0L, volume)));
            } catch (RuntimeException ex) {
                // Skip malformed rows rather than failing the whole file.
            }
        }
        return rows;
    }

    private static Map<String, Integer> header(String line) {
        Map<String, Integer> columns = new HashMap<>();
        String[] cells = line.split(",", -1);
        for (int i = 0; i < cells.length; i++) {
            columns.put(cells[i].trim().toLowerCase(Locale.ROOT), i);
        }
        return columns;
    }

    private static String cell(String[] cells, Integer index) {
        if (index == null || index < 0 || index >= cells.length) {
            return "";
        }
        return cells[index].trim();
    }

    private static String orZero(String value) {
        return value == null || value.isBlank() ? "0" : value;
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || value.equals("-")) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal positiveOr(BigDecimal value, BigDecimal fallback) {
        return value != null && value.signum() > 0 ? value : fallback;
    }
}
