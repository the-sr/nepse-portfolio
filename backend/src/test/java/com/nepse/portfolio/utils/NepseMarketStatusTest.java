package com.nepse.portfolio.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NepseMarketStatusTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void understandsTheKnownPayloadShapes() {
        assertThat(NepseApiClient.parseMarketStatus(mapper,
                "{\"open\":\"true\",\"businessDate\":\"2026-10-21\"}")).isTrue();
        assertThat(NepseApiClient.parseMarketStatus(mapper,
                "{\"open\":\"false\"}")).isFalse();
        assertThat(NepseApiClient.parseMarketStatus(mapper,
                "{\"marketOpen\":true}")).isTrue();
        assertThat(NepseApiClient.parseMarketStatus(mapper,
                "{\"status\":\"OPEN\"}")).isTrue();
        assertThat(NepseApiClient.parseMarketStatus(mapper,
                "{\"status\":\"CLOSED\"}")).isFalse();
        assertThat(NepseApiClient.parseMarketStatus(mapper,
                "not json")).isNull();
        assertThat(NepseApiClient.parseMarketStatus(mapper,
                "{\"something\":\"else\"}")).isNull();
    }
}
