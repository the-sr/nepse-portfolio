package com.nepse.portfolio.dto.portfolio;

import com.nepse.portfolio.domain.TradeType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PortfolioDtos {

    private PortfolioDtos() {
    }

    public record HoldingDto(
            Long stockId,
            String symbol,
            String companyName,
            String sector,
            int quantity,
            BigDecimal averageCost,
            BigDecimal ltp,
            BigDecimal prevClose,
            BigDecimal invested,
            BigDecimal currentValue,
            BigDecimal unrealizedProfitLoss,
            BigDecimal unrealizedPercent,
            BigDecimal dayChange,
            BigDecimal dayChangePercent,
            BigDecimal realizedProfitLoss,
            BigDecimal weightPercent
    ) {
    }

    public record SummaryDto(
            BigDecimal totalInvestment,
            BigDecimal currentValue,
            BigDecimal unrealizedProfitLoss,
            BigDecimal realizedProfitLoss,
            BigDecimal totalProfitLoss,
            BigDecimal totalProfitLossPercent,
            BigDecimal dayChange,
            BigDecimal dayChangePercent,
            int holdingsCount
    ) {
    }

    public record AllocationSlice(String label, BigDecimal value, BigDecimal percent) {
    }

    public record PortfolioDto(
            SummaryDto summary,
            List<HoldingDto> holdings,
            List<AllocationSlice> allocation,
            List<AllocationSlice> sectorAllocation
    ) {
    }

    public record TradeDto(
            Long id,
            Long stockId,
            String symbol,
            String companyName,
            String type,
            int quantity,
            BigDecimal price,
            BigDecimal brokerage,
            BigDecimal sebonFee,
            BigDecimal dpCharge,
            BigDecimal tax,
            BigDecimal fees,
            BigDecimal total,
            Instant tradeDate,
            String notes
    ) {
    }

    public record TradeRequest(
            @NotBlank String symbol,
            @NotNull TradeType type,
            @Min(1) int quantity,
            /** Allotment price; bonus shares book at Rs 0 (may be omitted). */
            @DecimalMin("0") BigDecimal price,
            /** Fee snapshots. Null fields mean "compute from the rate schedule";
             *  a provided value is kept exactly as entered. */
            @DecimalMin("0") BigDecimal brokerage,
            @DecimalMin("0") BigDecimal sebonFee,
            @DecimalMin("0") BigDecimal dpCharge,
            @DecimalMin("0") BigDecimal tax,
            Instant tradeDate,
            @Size(max = 500) String notes
    ) {
    }

    /** What booking a trade would cost, before it is saved. */
    public record ChargesPreviewDto(
            String symbol,
            String type,
            int quantity,
            BigDecimal price,
            BigDecimal amount,
            BigDecimal brokerage,
            BigDecimal sebonFee,
            BigDecimal dpCharge,
            BigDecimal cgt,
            BigDecimal cgtRate,
            int holdingDays,
            BigDecimal wacPerShare,
            BigDecimal totalCost,
            BigDecimal netProceeds,
            BigDecimal netProfit
    ) {
    }

    /** Per-scrip "if you sold everything today" tax report (WAC basis). */
    public record CgtLotDto(
            Long stockId,
            String symbol,
            String companyName,
            int quantity,
            BigDecimal wacPerShare,
            BigDecimal invested,
            BigDecimal ltp,
            BigDecimal gainIfSold,
            BigDecimal feesIfSold,
            BigDecimal cgtRate,
            BigDecimal estimatedCgt,
            BigDecimal netAfterTax,
            Instant earliestLotDate,
            int holdingDays,
            String slab
    ) {
    }

    public record CgtReportDto(
            BigDecimal totalGainIfSold,
            BigDecimal totalEstimatedCgt,
            BigDecimal totalNetAfterTax,
            List<CgtLotDto> lots
    ) {
    }

    public record HistoryPointDto(LocalDate date, BigDecimal value, BigDecimal investment,
                                  BigDecimal profitLoss) {
    }
}
