package com.nepse.portfolio.utils.nepse;

import lombok.extern.slf4j.Slf4j;
import com.nepse.portfolio.domain.User;
import com.nepse.portfolio.service.market.NepseTokenCleaner;
import com.nepse.portfolio.utils.nepse.NepseWasm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Fetches and caches an access token for NEPSE's undocumented public API.
 *
 * <p>The exchange issues very short lived tokens (~45 seconds), so the token is
 * cached for {@code app.nepse.token-ttl-seconds} and refreshed on demand.</p>
 */
@Component
@Slf4j
public class NepseAuthClient {


    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final NepseWasm wasm;

    private final String baseUrl;
    private final String tokenPath;
    private final String userAgent;
    private final Duration requestTimeout;
    private final Duration tokenTtl;

    private volatile CachedToken cached;

    public NepseAuthClient(ObjectMapper objectMapper,
                           NepseWasm wasm,
                           @Value("${app.nepse.base-url:https://www.nepalstock.com}") String baseUrl,
                           @Value("${app.nepse.token-path:/api/authenticate/prove}") String tokenPath,
                           @Value("${app.nepse.user-agent}") String userAgent,
                           @Value("${app.nepse.request-timeout-ms:15000}") long requestTimeoutMs,
                           @Value("${app.nepse.token-ttl-seconds:40}") long tokenTtlSeconds) {
        this.objectMapper = objectMapper;
        this.wasm = wasm;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.tokenPath = tokenPath;
        this.userAgent = userAgent;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.tokenTtl = Duration.ofSeconds(tokenTtlSeconds);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Returns a cleaned, currently valid access token. */
    public String accessToken() {
        CachedToken current = cached;
        if (current != null && current.isValid()) {
            return current.value();
        }
        synchronized (this) {
            current = cached;
            if (current != null && current.isValid()) {
                return current.value();
            }
            CachedToken fresh = fetchToken();
            cached = fresh;
            return fresh.value();
        }
    }

    /** Forces a refresh on the next call, e.g. after a 401 or an empty response. */
    public void invalidate() {
        cached = null;
    }

    private CachedToken fetchToken() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + tokenPath))
                .timeout(requestTimeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", baseUrl + "/")
                .header("Origin", baseUrl)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not reach NEPSE authentication endpoint: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while authenticating with NEPSE", ex);
        }

        if (response.statusCode() != 200) {
            throw new IllegalStateException("NEPSE authentication returned HTTP " + response.statusCode());
        }

        try {
            JsonNode node = objectMapper.readTree(response.body());
            String rawAccess = text(node, "accessToken");
            String refresh = text(node, "refreshToken");
            long[] salts = new long[5];
            for (int i = 0; i < salts.length; i++) {
                salts[i] = number(node, "salt" + (i + 1));
            }

            int[] indexes = wasm.tokenIndexes(salts);
            String access = NepseTokenCleaner.clean(rawAccess, indexes);
            log.debug("Obtained NEPSE token ({} -> {} chars)", rawAccess.length(), access.length());
            return new CachedToken(access, refresh, Instant.now().plus(tokenTtl));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not parse NEPSE authentication response", ex);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException("NEPSE authentication response is missing '" + field + "'");
        }
        return value.asText();
    }

    private long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalStateException("NEPSE authentication response is missing '" + field + "'");
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("NEPSE authentication field '" + field + "' is not a number", ex);
        }
    }

    private String stripTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private record CachedToken(String value, String refreshToken, Instant expiresAt) {

        boolean isValid() {
            return Instant.now().isBefore(expiresAt.minusSeconds(2));
        }
    }
}
