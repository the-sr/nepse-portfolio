package com.nepse.portfolio.service;

import java.util.Locale;
import java.util.Map;

public interface SecurityUniverseService {

    record Result(
            int activeSecurities,
            int companyRecords,
            int added,
            int updated,
            int deactivated,
            int withoutSector,
            Map<String, Integer> byInstrumentType
    ) {
    }

    Result sync();

    /** NEPSE's own classification, with a name-based fallback for promoter shares. */
    static String classifyFromName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        // NEPSE spells this both "Promoter" and "Promotor".
        if (lower.contains("promot")) {
            return "Promoter Shares";
        }
        if (lower.contains("debenture") || lower.contains("bond")) {
            return "Non-Convertible Debentures";
        }
        if (lower.contains("mutual fund") || lower.contains("scheme") || lower.contains("fund")) {
            return "Mutual Funds";
        }
        if (lower.contains("preference")) {
            return "Preference Shares";
        }
        return "Equity";
    }

    /**
     * Promoter shares use a suffixed ticker (NABIL -> NABILP / BFCPO). Mapping
     * back to the parent company supplies their sector.
     */
    static String baseSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        if (symbol.endsWith("PO") && symbol.length() > 2) {
            return symbol.substring(0, symbol.length() - 2);
        }
        if (symbol.endsWith("P") && symbol.length() > 1) {
            return symbol.substring(0, symbol.length() - 1);
        }
        return symbol;
    }
}
