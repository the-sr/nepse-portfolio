package com.nepse.portfolio.market.nepse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nepse.portfolio.market.scraper.ScrapedQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NepseApiProviderTest {

    private static final String JSON = """
            [
              {
                "securityId": "2790",
                "securityName": "Aarambha Chautari Laghubitta Bittiya Sanstha Limited",
                "symbol": "ACLBSL",
                "indexId": 58,
                "openPrice": 850.10,
                "highPrice": 855.00,
                "lowPrice": 846.00,
                "totalTradeQuantity": 892,
                "totalTradeValue": 13723302.90,
                "lastTradedPrice": 850.00,
                "percentageChange": 1.19,
                "lastUpdatedDateTime": "2026-10-16 15:00:00.000876",
                "lastTradedVolume": 234,
                "previousClose": 840.00,
                "averageTradedPrice": 848.30
              },
              {
                "symbol": "ZERO",
                "lastTradedPrice": 0
              }
            ]
            """;

    @Test
    void mapsLiveMarketRowsToQuotes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<NepseLiveQuote> rows = mapper.readValue(JSON,
                mapper.getTypeFactory().constructCollectionType(List.class, NepseLiveQuote.class));

        List<ScrapedQuote> quotes = rows.stream()
                .map(NepseApiProvider::toQuote)
                .flatMap(java.util.Optional::stream)
                .toList();

        assertThat(quotes).hasSize(1);
        ScrapedQuote quote = quotes.get(0);
        assertThat(quote.symbol()).isEqualTo("ACLBSL");
        assertThat(quote.ltp()).isEqualByComparingTo(new BigDecimal("850.00"));
        assertThat(quote.open()).isEqualByComparingTo(new BigDecimal("850.10"));
        assertThat(quote.high()).isEqualByComparingTo(new BigDecimal("855.00"));
        assertThat(quote.low()).isEqualByComparingTo(new BigDecimal("846.00"));
        assertThat(quote.prevClose()).isEqualByComparingTo(new BigDecimal("840.00"));
        assertThat(quote.changePercent()).isEqualByComparingTo(new BigDecimal("1.19"));
        assertThat(quote.volume()).isEqualTo(892);
        assertThat(quote.turnover()).isEqualByComparingTo(new BigDecimal("13723302.90"));
        assertThat(quote.asOf()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    void skipsRowsWithoutAValidPrice() {
        NepseLiveQuote blank = new NepseLiveQuote("ACLBSL", null, null, null, null, null, null,
                null, null, null);
        NepseLiveQuote zero = new NepseLiveQuote("ACLBSL", null, null, null, BigDecimal.ZERO,
                null, null, null, null, null);

        assertThat(NepseApiProvider.toQuote(blank)).isEmpty();
        assertThat(NepseApiProvider.toQuote(zero)).isEmpty();
    }

    @Test
    void fillsMissingFieldsFromLastTradedPrice() {
        NepseLiveQuote sparse = new NepseLiveQuote("NABIL", null, null, null,
                new BigDecimal("500"), null, null, null, null, null);

        ScrapedQuote quote = NepseApiProvider.toQuote(sparse).orElseThrow();

        assertThat(quote.open()).isEqualByComparingTo("500");
        assertThat(quote.high()).isEqualByComparingTo("500");
        assertThat(quote.low()).isEqualByComparingTo("500");
        assertThat(quote.prevClose()).isEqualByComparingTo("500");
        assertThat(quote.volume()).isZero();
        assertThat(quote.asOf()).isNull();
    }

    @Test
    void parsesNepseTimestamps() {
        assertThat(NepseApiProvider.parseDate("2026-10-16 15:00:00.000876"))
                .isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(NepseApiProvider.parseDate("")).isNull();
        assertThat(NepseApiProvider.parseDate(null)).isNull();
        assertThat(NepseApiProvider.parseDate("not-a-date")).isNull();
    }
}
