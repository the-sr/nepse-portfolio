package com.nepse.portfolio.service.market;

import com.nepse.portfolio.service.market.NepseStockSyncService;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NepseStockSyncServiceTest {

    @Test
    void classifiesInstrumentsFromTheirNames() {
        assertThat(NepseStockSyncService.classifyFromName("Nabil Bank Limited")).isEqualTo("Equity");
        assertThat(NepseStockSyncService.classifyFromName("Nabil Bank Limited Promoter Share"))
                .isEqualTo("Promoter Shares");
        assertThat(NepseStockSyncService.classifyFromName("Nabil Bank Limited Promotor Share"))
                .isEqualTo("Promoter Shares");
        assertThat(NepseStockSyncService.classifyFromName("10.35% Agricultural Bank Debenture 2083"))
                .isEqualTo("Non-Convertible Debentures");
        assertThat(NepseStockSyncService.classifyFromName("Nabil Balanced Fund 2"))
                .isEqualTo("Mutual Funds");
        assertThat(NepseStockSyncService.classifyFromName("Nabil Preference Share"))
                .isEqualTo("Preference Shares");
    }

    @Test
    void mapsPromoterTickersBackToTheirParent() {
        assertThat(NepseStockSyncService.baseSymbol("NABILP")).isEqualTo("NABIL");
        assertThat(NepseStockSyncService.baseSymbol("BFCPO")).isEqualTo("BFC");
        assertThat(NepseStockSyncService.baseSymbol("NABIL")).isEqualTo("NABIL");
        assertThat(NepseStockSyncService.baseSymbol(null)).isEmpty();
    }
}