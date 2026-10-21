package com.nepse.portfolio.service;

import com.nepse.portfolio.utils.NepseApiClient;
import com.nepse.portfolio.utils.NepseCompany;
import com.nepse.portfolio.utils.NepseSecurity;

import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Synchronises the {@code stocks} table with NEPSE's live security universe.
 *
 * <p>NEPSE lists equities, mutual fund units, debentures and promoter shares as
 * separate securities. All of them are imported, tagged with NEPSE's own
 * {@code instrumentType} and sector name. Securities that disappear from the
 * exchange's active list are marked inactive rather than deleted, because
 * trades, alerts and price history reference them.</p>
 */
@Service
public class SecurityUniverseService {

    private static final Logger log = LoggerFactory.getLogger(SecurityUniverseService.class);

    private final NepseApiClient apiClient;
    private final StockRepository stockRepository;
    private final TransactionTemplate transaction;

    public SecurityUniverseService(NepseApiClient apiClient,
                                   StockRepository stockRepository,
                                   PlatformTransactionManager transactionManager) {
        this.apiClient = apiClient;
        this.stockRepository = stockRepository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public record Result(
            int activeSecurities,
            int companyRecords,
            int added,
            int updated,
            int deactivated,
            int withoutSector,
            Map<String, Integer> byInstrumentType
    ) {
    }

    public Result sync() {
        List<NepseSecurity> securities = apiClient.fetchActiveSecurities();
        List<NepseCompany> companies = apiClient.fetchCompanyList();

        Result result = transaction.execute(status -> persist(securities, companies));
        if (result == null) {
            throw new IllegalStateException("Universe sync was rolled back");
        }
        log.info("Universe sync: {} securities, {} added, {} updated, {} deactivated",
                result.activeSecurities(), result.added(), result.updated(), result.deactivated());
        return result;
    }

    private Result persist(List<NepseSecurity> securities, List<NepseCompany> companies) {
        Map<String, NepseCompany> bySymbol = companies.stream()
                .filter(c -> c.symbol() != null)
                .collect(Collectors.toMap(c -> normalize(c.symbol()), Function.identity(),
                        (first, second) -> first));

        Map<String, Stock> existing = stockRepository.findAll().stream()
                .collect(Collectors.toMap(s -> normalize(s.getSymbol()), Function.identity(),
                        (first, second) -> first));

        Set<String> seen = new HashSet<>();
        Map<String, Integer> byType = new TreeMap<>();
        List<Stock> toSave = new ArrayList<>();
        int added = 0;
        int updated = 0;
        int withoutSector = 0;

        for (NepseSecurity security : securities) {
            if (security.symbol() == null || security.symbol().isBlank()) {
                continue;
            }
            String symbol = normalize(security.symbol());
            if (!seen.add(symbol)) {
                continue;
            }

            NepseCompany exact = bySymbol.get(symbol);
            NepseCompany company = exact != null ? exact : bySymbol.get(baseSymbol(symbol));

            String name = firstNonBlank(security.securityName(),
                    company == null ? null : company.companyName());
            if (name == null) {
                name = symbol;
            }

            String sector = company == null ? null : trimToNull(company.sectorName());
            if (sector == null) {
                withoutSector++;
            }

            String instrumentType = exact != null
                    ? firstNonBlank(exact.instrumentType(), "Equity")
                    : classifyFromName(name);
            byType.merge(instrumentType, 1, Integer::sum);

            Stock stock = existing.get(symbol);
            if (stock == null) {
                stock = new Stock(symbol, name, sector, instrumentType);
                existing.put(symbol, stock);
                toSave.add(stock);
                added++;
            } else {
                boolean changed = false;
                if (sector != null && !sector.equals(stock.getSector())) {
                    stock.setSector(sector);
                    changed = true;
                }
                if (instrumentType != null && !instrumentType.equals(stock.getInstrumentType())) {
                    stock.setInstrumentType(instrumentType);
                    changed = true;
                }
                if (!stock.isActive()) {
                    stock.setActive(true);
                    changed = true;
                }
                if (changed) {
                    updated++;
                }
            }
        }

        int deactivated = 0;
        for (Stock stock : existing.values()) {
            if (!seen.contains(normalize(stock.getSymbol())) && stock.isActive()) {
                stock.setActive(false);
                toSave.add(stock);
                deactivated++;
            }
        }

        stockRepository.saveAll(toSave);

        return new Result(securities.size(), companies.size(), added, updated,
                deactivated, withoutSector, new LinkedHashMap<>(byType));
    }

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

    private static String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(second);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
