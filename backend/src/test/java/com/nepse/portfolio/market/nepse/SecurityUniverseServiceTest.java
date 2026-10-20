package com.nepse.portfolio.market.nepse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUniverseServiceTest {

    @Test
    void classifiesInstrumentsFromTheirNames() {
        assertThat(SecurityUniverseService.classifyFromName("Nabil Bank Limited")).isEqualTo("Equity");
        assertThat(SecurityUniverseService.classifyFromName("Nabil Bank Limited Promoter Share"))
                .isEqualTo("Promoter Shares");
        assertThat(SecurityUniverseService.classifyFromName("Nabil Bank Limited Promotor Share"))
                .isEqualTo("Promoter Shares");
        assertThat(SecurityUniverseService.classifyFromName("10.35% Agricultural Bank Debenture 2083"))
                .isEqualTo("Non-Convertible Debentures");
        assertThat(SecurityUniverseService.classifyFromName("Nabil Balanced Fund 2"))
                .isEqualTo("Mutual Funds");
        assertThat(SecurityUniverseService.classifyFromName("Nabil Preference Share"))
                .isEqualTo("Preference Shares");
    }

    @Test
    void mapsPromoterTickersBackToTheirParent() {
        assertThat(SecurityUniverseService.baseSymbol("NABILP")).isEqualTo("NABIL");
        assertThat(SecurityUniverseService.baseSymbol("BFCPO")).isEqualTo("BFC");
        assertThat(SecurityUniverseService.baseSymbol("NABIL")).isEqualTo("NABIL");
        assertThat(SecurityUniverseService.baseSymbol(null)).isEmpty();
    }
}
