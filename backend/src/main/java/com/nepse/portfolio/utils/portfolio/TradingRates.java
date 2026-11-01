package com.nepse.portfolio.utils.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record TradingRates(List<RateTier> commissionTiers, BigDecimal sebonFeePercent,
                           BigDecimal dpChargePerScripPerDay, BigDecimal cgtShortRate, BigDecimal cgtLongRate,
                           Integer cgtLongThresholdDays, Boolean buyFeesIncludedInBasis) {

    public record RateTier(BigDecimal upTo, BigDecimal percent, BigDecimal flat) {
    }

    public static final TradingRates DEFAULT = new TradingRates(
            List.of(
                    new RateTier(new BigDecimal("50000"), new BigDecimal("0.36"), null),
                    new RateTier(new BigDecimal("500000"), new BigDecimal("0.33"), null),
                    new RateTier(new BigDecimal("2000000"), new BigDecimal("0.31"), null),
                    new RateTier(new BigDecimal("10000000"), new BigDecimal("0.27"), null),
                    new RateTier(null, new BigDecimal("0.24"), null)
            ),
            new BigDecimal("0.015"),
            new BigDecimal("25"),
            new BigDecimal("5.0"),
            new BigDecimal("3.75"),
            365,
            true
    );

    public static TradingRates merge(TradingRates base, TradingRates override) {
        if (override == null) return base;
        return new TradingRates(
                override.commissionTiers() != null ? override.commissionTiers() : base.commissionTiers(),
                override.sebonFeePercent() != null ? override.sebonFeePercent() : base.sebonFeePercent(),
                override.dpChargePerScripPerDay() != null ? override.dpChargePerScripPerDay() : base.dpChargePerScripPerDay(),
                override.cgtShortRate() != null ? override.cgtShortRate() : base.cgtShortRate(),
                override.cgtLongRate() != null ? override.cgtLongRate() : base.cgtLongRate(),
                override.cgtLongThresholdDays() != null ? override.cgtLongThresholdDays() : base.cgtLongThresholdDays(),
                override.buyFeesIncludedInBasis() != null ? override.buyFeesIncludedInBasis() : base.buyFeesIncludedInBasis()
        );
    }
}
