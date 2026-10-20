package com.nepse.portfolio.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record RealizedGainRow(
            String symbol,
            String companyName,
            int quantity,
            LocalDate buyDate,
            LocalDate sellDate,
            long holdingDays,
            BigDecimal costBasis,
            BigDecimal proceeds,
            BigDecimal profitLoss,
            BigDecimal taxRatePercent,
            BigDecimal estimatedTax
    ) {
    }

    public record RealizedGainsReport(
            LocalDate from,
            LocalDate to,
            BigDecimal totalCostBasis,
            BigDecimal totalProceeds,
            BigDecimal totalProfitLoss,
            BigDecimal estimatedTax,
            int saleCount,
            List<RealizedGainRow> rows
    ) {
    }
}
