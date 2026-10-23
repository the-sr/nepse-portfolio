package com.nepse.portfolio.utils.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * The Nepali cost/tax schedule as configuration (docs/nepse-costs.md).
 *
 * <p>The commission the broker quotes bundles NEPSE (20%) and SEBON (0.6%)
 * shares, so one tiered table covers all three. SEBON's 0.015% transaction fee
 * and the Rs-25-per-scrip-per-day DP charge are separate line items, and CGT
 * slabs come from the Income Tax Act (weighted-average cost basis).</p>
 *
 * <p>Every field is optional so a user/admin document can override a subset;
 * the resolver merges field-wise over the defaults.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TradingRates(
        @JsonProperty("commissionTiers") List<RateTier> commissionTiers,
        @JsonProperty("sebonFeePercent") BigDecimal sebonFeePercent,
        @JsonProperty("dpChargePerScripPerDay") BigDecimal dpChargePerScripPerDay,
        @JsonProperty("cgtShortRate") BigDecimal cgtShortRate,
        @JsonProperty("cgtLongRate") BigDecimal cgtLongRate,
        @JsonProperty("cgtLongThresholdDays") Integer cgtLongThresholdDays,
        @JsonProperty("buyFeesIncludedInBasis") Boolean buyFeesIncludedInBasis
) {
    /** A commission slab: transaction amounts up to {@code upTo} (null = unbounded) charge
     *  {@code percent}% of the amount, or a {@code flat} rupee minimum — whichever is higher. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RateTier(
            @JsonProperty("upTo") BigDecimal upTo,
            @JsonProperty("percent") BigDecimal percent,
            @JsonProperty("flat") BigDecimal flat
    ) {
    }

    /** Verified 2026-10: broker tiers (flat Rs 10 minimum), SEBON 0.015% both sides,
     *  DP Rs 25/scrip/day on sells, CGT 5% (<=365d) / 3.75% (>365d) per Budget 2083/84. */
    public static final TradingRates DEFAULT = new TradingRates(
            List.of(
                    new RateTier(new BigDecimal("2500"), null, new BigDecimal("10")),
                    new RateTier(new BigDecimal("50000"), new BigDecimal("0.36"), null),
                    new RateTier(new BigDecimal("500000"), new BigDecimal("0.33"), null),
                    new RateTier(new BigDecimal("2000000"), new BigDecimal("0.31"), null),
                    new RateTier(null, new BigDecimal("0.30"), null)
            ),
            new BigDecimal("0.015"),
            new BigDecimal("25"),
            new BigDecimal("5.0"),
            new BigDecimal("3.75"),
            365,
            true
    );

    /** Field-wise merge: upper layer's non-null values win over base's. */
    public static TradingRates merge(TradingRates base, TradingRates override) {
        if (override == null) {
            return base;
        }
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
