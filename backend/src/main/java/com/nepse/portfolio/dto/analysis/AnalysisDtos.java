package com.nepse.portfolio.dto.analysis;

import com.nepse.portfolio.dto.analysis.Indicators;

import com.nepse.portfolio.dto.market.MarketDtos.StockDto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    public record RecommendationDto(
            String symbol,
            String companyName,
            BigDecimal ltp,
            BigDecimal changePercent,
            String action,
            int score,
            BigDecimal confidence,
            String source,
            List<String> reasons,
            Indicators indicators,
            Integer holdingQuantity,
            BigDecimal averageCost,
            BigDecimal profitLoss,
            BigDecimal profitLossPercent,
            Instant generatedAt
    ) {
    }

    public record StockAnalysisDto(StockDto stock, Indicators indicators, RecommendationDto recommendation) {
    }

    public record DcaOpportunityDto(
            String symbol,
            String companyName,
            int quantity,
            BigDecimal averageCost,
            BigDecimal ltp,
            BigDecimal lossPercent,
            String level,
            int suggestedQuantity,
            BigDecimal suggestedBudget,
            BigDecimal newAverageCost,
            BigDecimal averageImprovementPercent,
            String reason
    ) {
    }

    public record AverageSimulationRequest(
            @NotBlank String symbol,
            @NotBlank @Pattern(regexp = "QTY|BUDGET|TARGET", message = "must be QTY, BUDGET or TARGET") String mode,
            @Min(1) Integer quantity,
            @DecimalMin("1") BigDecimal budget,
            @DecimalMin("0.01") BigDecimal targetAverage,
            @DecimalMin("0.01") BigDecimal price
    ) {
    }

    public record AverageSimulationResponse(
            String symbol,
            int currentQuantity,
            BigDecimal currentAverageCost,
            BigDecimal price,
            int addedQuantity,
            BigDecimal addedCapital,
            int newQuantity,
            BigDecimal newAverageCost,
            BigDecimal newInvestment,
            BigDecimal averageReductionPercent,
            boolean feasible,
            String message
    ) {
    }
}
