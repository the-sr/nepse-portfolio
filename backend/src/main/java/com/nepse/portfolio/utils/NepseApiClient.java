package com.nepse.portfolio.utils;

import com.nepse.portfolio.domain.User;
import com.nepse.portfolio.utils.NepseAuthClient;
import com.nepse.portfolio.utils.NepseCompany;
import com.nepse.portfolio.utils.NepseLiveQuote;
import com.nepse.portfolio.utils.NepseSecurity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Thin HTTP client for NEPSE's undocumented public endpoints.
 *
 * <p>
 * Requests carry {@code Authorization: Salter <token>}. A stale token is not
 * always rejected with 401 - NEPSE sometimes answers {@code 200} with an empty
 * body - so an empty payload triggers a token refresh and a single retry.
 * </p>
 */
@Component
public class NepseApiClient {

    private static final Logger log = LoggerFactory.getLogger(NepseApiClient.class);

    private static final TypeReference<List<NepseLiveQuote>> QUOTE_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<NepseSecurity>> SECURITY_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<NepseCompany>> COMPANY_LIST = new TypeReference<>() {
    };

    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final NepseAuthClient authClient;

    private final String baseUrl;
    private final String liveMarketPath;
    private final String marketOpenPath;
    private final String securityListPath;
    private final String companyListPath;
    private final String userAgent;
    private final Duration requestTimeout;

    public NepseApiClient(ObjectMapper objectMapper,
            NepseAuthClient authClient,
            @Value("${app.nepse.base-url:https://www.nepalstock.com}") String baseUrl,
            @Value("${app.nepse.live-market-path:/api/nots/lives-market}") String liveMarketPath,
            @Value("${app.nepse.market-open-path:/api/nots/nepse-data/market-open}") String marketOpenPath,
            @Value("${app.nepse.security-list-path:/api/nots/security?nonDelisted=true}") String securityListPath,
            @Value("${app.nepse.company-list-path:/api/nots/company/list}") String companyListPath,
            @Value("${app.nepse.user-agent}") String userAgent,
            @Value("${app.nepse.request-timeout-ms:15000}") long requestTimeoutMs) {
        this.objectMapper = objectMapper;
        this.authClient = authClient;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.liveMarketPath = liveMarketPath;
        this.marketOpenPath = marketOpenPath;
        this.securityListPath = securityListPath;
        this.companyListPath = companyListPath;
        this.userAgent = userAgent;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Fetches the live (or most recent) quotes for every traded security. */
    public List<NepseLiveQuote> fetchLiveMarket() {
        List<NepseLiveQuote> quotes = getList(liveMarketPath, QUOTE_LIST);
        log.debug("NEPSE API returned {} quotes", quotes.size());
        return quotes;
    }

    /** Every non-delisted security, including bonds, funds and promoter shares. */
    public List<NepseSecurity> fetchActiveSecurities() {
        return getList(securityListPath, SECURITY_LIST);
    }

    /**
     * Tolerant probe of NEPSE's market-status endpoint. Returns an empty
     * {@link Optional} when the endpoint is down or the payload is
     * unrecognised - the caller then falls back to its own calendar.
     */
    public Optional<Boolean> fetchMarketStatus() {
        try {
            Boolean open = parseMarketStatus(objectMapper, fetchBody(marketOpenPath));
            if (open == null) {
                log.warn("NEPSE market status endpoint returned an unexpected payload");
                return Optional.empty();
            }
            return Optional.of(open);
        } catch (Exception ex) {
            log.warn("NEPSE market status unavailable: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Accepts every payload shape NEPSE has shipped so far, e.g.
     * {@code {"open":"true","businessDate":"2026-10-21"}},
     * {@code {"marketOpen":true}} or {@code {"status":"OPEN"}}.
     * {@code null} means "did not understand".
     */
    static Boolean parseMarketStatus(ObjectMapper mapper, String body) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (IOException ex) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        for (String field : List.of("open", "marketOpen", "isOpen", "isMarketOpen",
                "marketStatus", "status")) {
            JsonNode value = root.path(field);
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isNumber() && value.asText().length() <= 1) {
                return value.asInt() != 0;
            }
            String text = value.asText("").trim().toUpperCase(Locale.ROOT);
            if (text.equals("TRUE") || text.equals("Y") || text.equals("YES")
                    || text.equals("OPEN") || text.equals("TRADING")) {
                return true;
            }
            if (text.equals("FALSE") || text.equals("N") || text.equals("NO") || text.equals("CLOSED")
                    || text.equals("CLOSE")) {
                return false;
            }
        }
        return null;
    }

    /** Company metadata: name, sector and NEPSE's instrument classification. */
    public List<NepseCompany> fetchCompanyList() {
        return getList(companyListPath, COMPANY_LIST);
    }

    private <T> List<T> getList(String path, TypeReference<List<T>> type) {
        String body = fetchBody(path);
        try {
            List<T> values = objectMapper.readValue(body, type);
            if (values == null || values.isEmpty()) {
                throw new IllegalStateException("NEPSE API " + path + " returned no rows");
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not parse NEPSE API response from " + path, ex);
        }
    }

    private String fetchBody(String path) {
        IllegalStateException lastFailure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt > 0) {
                authClient.invalidate();
            }
            HttpResponse<String> response = send(path, authClient.accessToken());
            int status = response.statusCode();

            if (status == 401 || status == 403) {
                lastFailure = new IllegalStateException(
                        "NEPSE API rejected the access token (HTTP " + status + ")");
                continue;
            }
            if (status != 200) {
                throw new IllegalStateException("NEPSE API " + path + " returned HTTP " + status);
            }
            String body = response.body();
            if (body == null || body.isBlank() || body.trim().equals("[]")) {
                lastFailure = new IllegalStateException("NEPSE API " + path + " returned an empty response");
                continue;
            }
            return body;
        }
        throw lastFailure == null
                ? new IllegalStateException("NEPSE API request failed")
                : lastFailure;
    }

    private HttpResponse<String> send(String path, String token) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", baseUrl + "/")
                .header("Origin", baseUrl)
                .header("Authorization", "Salter " + token)
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not reach NEPSE API: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling NEPSE API", ex);
        }
    }

    private String stripTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
