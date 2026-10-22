package com.nepse.portfolio.service.analysis;

import com.nepse.portfolio.service.analysis.DcaService;

import com.nepse.portfolio.dto.analysis.AnalysisDtos.AverageSimulationRequest;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.AverageSimulationResponse;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.DcaOpportunityDto;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.HoldingDto;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.PortfolioDto;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.SummaryDto;
import com.nepse.portfolio.service.portfolio.PortfolioService;
import com.nepse.portfolio.service.settings.SettingsService;
import com.nepse.portfolio.domain.UserSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DcaServiceTest {

    private PortfolioService portfolioService;
    private DcaService dcaService;

    private HoldingDto holding() {
        return new HoldingDto(
                1L, "NICA", "NIC Asia Bank", "Commercial Bank",
                100, new BigDecimal("100.00"), new BigDecimal("85.00"), new BigDecimal("88.00"),
                new BigDecimal("10000.00"), new BigDecimal("8500.00"),
                new BigDecimal("-1500.00"), new BigDecimal("-15.00"),
                new BigDecimal("-300.00"), new BigDecimal("-3.41"),
                BigDecimal.ZERO, new BigDecimal("8.50")
        );
    }

    private PortfolioDto portfolio() {
        SummaryDto summary = new SummaryDto(
                new BigDecimal("100000.00"), new BigDecimal("98500.00"),
                new BigDecimal("-1500.00"), BigDecimal.ZERO, new BigDecimal("-1500.00"),
                new BigDecimal("-1.50"), new BigDecimal("-300.00"), new BigDecimal("-0.30"), 1
        );
        return new PortfolioDto(summary, List.of(holding()), List.of(), List.of());
    }

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        SettingsService settingsService = mock(SettingsService.class);
        UserSettings settings = new UserSettings(1L);
        when(portfolioService.portfolio(anyLong())).thenReturn(portfolio());
        when(portfolioService.holdings(anyLong())).thenReturn(List.of(holding()));
        when(settingsService.loadOrCreate(anyLong())).thenReturn(settings);
        dcaService = new DcaServiceImpl(portfolioService, settingsService);
    }

    @Test
    void flagsAnAggressiveAveragingOpportunity() {
        List<DcaOpportunityDto> opportunities = dcaService.opportunities(1L);

        assertThat(opportunities).hasSize(1);
        DcaOpportunityDto opportunity = opportunities.get(0);
        assertThat(opportunity.level()).isEqualTo("AGGRESSIVE");
        assertThat(opportunity.suggestedQuantity()).isPositive();
        assertThat(opportunity.newAverageCost()).isLessThan(new BigDecimal("100.00"));
        assertThat(opportunity.averageImprovementPercent()).isPositive();
    }

    @Test
    void computesSharesNeededToReachATargetAverage() {
        AverageSimulationResponse response = dcaService.simulate(1L,
                new AverageSimulationRequest("NICA", "TARGET", null, null,
                        new BigDecimal("90.00"), new BigDecimal("85.00")));

        assertThat(response.feasible()).isTrue();
        assertThat(response.addedQuantity()).isEqualTo(200);
        assertThat(response.newAverageCost()).isEqualByComparingTo("90.00");
    }

    @Test
    void rejectsTargetsBelowTheBuyPrice() {
        AverageSimulationResponse response = dcaService.simulate(1L,
                new AverageSimulationRequest("NICA", "TARGET", null, null,
                        new BigDecimal("80.00"), new BigDecimal("85.00")));

        assertThat(response.feasible()).isFalse();
        assertThat(response.addedQuantity()).isZero();
    }

    @Test
    void budgetModeBuysWholeShares() {
        AverageSimulationResponse response = dcaService.simulate(1L,
                new AverageSimulationRequest("NICA", "BUDGET", null, new BigDecimal("10000.00"),
                        null, new BigDecimal("85.00")));

        assertThat(response.feasible()).isTrue();
        assertThat(response.addedQuantity()).isEqualTo(117);
        assertThat(response.newQuantity()).isEqualTo(217);
        assertThat(response.newAverageCost()).isLessThan(new BigDecimal("100.00"));
    }
}
