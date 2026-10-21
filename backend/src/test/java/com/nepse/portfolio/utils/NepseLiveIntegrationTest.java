package com.nepse.portfolio.utils;

import com.nepse.portfolio.utils.NepseApiClient;
import com.nepse.portfolio.utils.NepseAuthClient;
import com.nepse.portfolio.utils.NepseLiveQuote;
import com.nepse.portfolio.utils.NepseWasm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real NEPSE API end to end (token handshake + wasm + live market).
 * Disabled by default; run with {@code NEPSE_LIVE_TEST=true mvn test}.
 */
@EnabledIfEnvironmentVariable(named = "NEPSE_LIVE_TEST", matches = "true")
class NepseLiveIntegrationTest {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    @Test
    void fetchesLiveQuotes() {
        ObjectMapper mapper = new ObjectMapper();
        NepseWasm wasm = new NepseWasm();
        NepseAuthClient auth = new NepseAuthClient(mapper, wasm,
                "https://www.nepalstock.com", "/api/authenticate/prove",
                USER_AGENT, 20_000, 40);
        NepseApiClient client = new NepseApiClient(mapper, auth,
                "https://www.nepalstock.com", "/api/nots/lives-market",
                "/api/nots/security?nonDelisted=true", "/api/nots/company/list",
                USER_AGENT, 20_000);

        List<NepseLiveQuote> quotes = client.fetchLiveMarket();

        assertThat(quotes).isNotEmpty();
        assertThat(quotes).anySatisfy(quote -> {
            assertThat(quote.symbol()).isNotBlank();
            assertThat(quote.lastTradedPrice()).isNotNull();
        });
        System.out.println("NEPSE live quotes fetched: " + quotes.size()
                + " (first: " + quotes.get(0).symbol() + " @ " + quotes.get(0).lastTradedPrice() + ")");
    }
}
