package com.nepse.portfolio.utils.scraper;

import lombok.extern.slf4j.Slf4j;
import com.nepse.portfolio.utils.scraper.ScrapedQuote;

import com.nepse.portfolio.utils.scraper.MarketDataProvider;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the Sharesansar live-trading table. Selectors are intentionally resilient:
 * we map columns by header name first and only fall back to fixed positions.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SharesansarScraper implements MarketDataProvider {

    public static final String PROVIDER_NAME = "sharesansar";


    @Value("${app.scraper.live-url}")
    private String liveUrl;
    @Value("${app.scraper.user-agent}")
    private String userAgent;
    @Value("${app.scraper.request-timeout-ms:15000}")
    private int timeoutMs;

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public String description() {
        return "Sharesansar live-trading table";
    }

    public List<ScrapedQuote> fetch() {
        try {
            Document document = Jsoup.connect(liveUrl)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .get();
            return parse(document);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not fetch " + liveUrl + ": " + ex.getMessage(), ex);
        }
    }

    public List<ScrapedQuote> parse(Document document) {
        Element table = document.selectFirst("table#DataTables_Table_0");
        if (table == null) {
            table = document.selectFirst("table.table");
        }
        if (table == null) {
            log.warn("No live-trading table found on Sharesansar page");
            return List.of();
        }

        Map<String, Integer> columns = headerColumns(table);
        List<ScrapedQuote> quotes = new ArrayList<>();

        for (Element row : table.select("tbody tr")) {
            Elements cells = row.select("td");
            if (cells.size() < 3) {
                continue;
            }
            String symbol = text(cells, columns.get("symbol"), 1).toUpperCase(Locale.ROOT);
            if (symbol.isBlank() || symbol.length() > 20) {
                symbol = text(cells, 1, 1).toUpperCase(Locale.ROOT);
            }
            if (symbol.isBlank() || !symbol.matches("[A-Z0-9._-]{1,20}")) {
                continue;
            }

            BigDecimal ltp = decimal(text(cells, columns.get("ltp"), 2));
            if (ltp == null || ltp.signum() <= 0) {
                continue;
            }

            BigDecimal open = decimal(text(cells, columns.get("open"), 4));
            BigDecimal high = decimal(text(cells, columns.get("high"), 5));
            BigDecimal low = decimal(text(cells, columns.get("low"), 6));
            BigDecimal prevClose = decimal(text(cells, columns.get("prevClose"), 8));
            BigDecimal changePercent = decimal(text(cells, columns.get("change"), 3));
            BigDecimal turnover = decimal(text(cells, columns.get("turnover"), 9));
            BigDecimal volumeValue = decimal(text(cells, columns.get("volume"), 7));
            long volume = volumeValue == null ? 0L : volumeValue.longValue();

            if (prevClose == null && changePercent != null && changePercent.doubleValue() != -100) {
                double divisor = 1 + changePercent.doubleValue() / 100.0;
                if (divisor > 0) {
                    prevClose = ltp.divide(BigDecimal.valueOf(divisor), 2, java.math.RoundingMode.HALF_UP);
                }
            }
            if (prevClose == null || prevClose.signum() <= 0) {
                prevClose = ltp;
            }

            quotes.add(new ScrapedQuote(
                    symbol,
                    ltp,
                    open == null ? ltp : open,
                    high == null ? ltp : high,
                    low == null ? ltp : low,
                    prevClose,
                    changePercent == null ? BigDecimal.ZERO : changePercent,
                    volume,
                    turnover == null ? BigDecimal.ZERO : turnover,
                    null
            ));
        }

        log.info("Parsed {} quotes from Sharesansar", quotes.size());
        return quotes;
    }

    private Map<String, Integer> headerColumns(Element table) {
        Map<String, Integer> columns = new HashMap<>();
        Elements headers = table.select("thead th");
        for (int i = 0; i < headers.size(); i++) {
            String header = normalize(headers.get(i).text());
            if (header.isEmpty()) {
                continue;
            }
            if (header.contains("symbol") || header.equals("scrip")) {
                columns.put("symbol", i);
            } else if (header.equals("ltp") || header.contains("last traded") || header.contains("last price")) {
                columns.put("ltp", i);
            } else if (header.contains("change")) {
                columns.put("change", i);
            } else if (header.equals("open") || header.startsWith("open ")) {
                columns.put("open", i);
            } else if (header.equals("high") || header.startsWith("high ")) {
                columns.put("high", i);
            } else if (header.equals("low") || header.startsWith("low ")) {
                columns.put("low", i);
            } else if (header.contains("volume") || header.equals("qty") || header.equals("quantity")) {
                columns.put("volume", i);
            } else if (header.contains("prev")) {
                columns.put("prevClose", i);
            } else if (header.contains("turnover")) {
                columns.put("turnover", i);
            }
        }
        return columns;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace(".", "")
                .replace("%", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String text(Elements cells, Integer index, int fallback) {
        int i = index == null ? fallback : index;
        return i >= 0 && i < cells.size() ? cells.get(i).text().trim() : "";
    }

    private BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) {
            return null;
        }
        String cleaned = raw.replace(",", "")
                .replace("रु", "")
                .replaceAll("[^0-9.\\-]", "")
                .trim();
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
