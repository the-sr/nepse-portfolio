package com.nepse.portfolio.utils.portfolio;

import com.nepse.portfolio.domain.Trade;
import com.nepse.portfolio.domain.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the Nepal cost schedule (docs/nepse-costs.md): the tiered
 * broker+NEPSE+SEBON commission, the separate SEBON fee, free allottment
 * types, CGT slabs, and the weighted-average ledger including bonus
 * dilution and same-day ordering.
 */
class NepseCostEngineTest {

    private static final TradingRates RATES = TradingRates.DEFAULT;
    private static final ZoneId ZONE = ZoneId.of("Asia/Kathmandu");

    private static Trade buy(String price, int qty, Instant when) {
        return new Trade(1L, 1L, TradeType.BUY, qty, new BigDecimal(price),
                null, null, null, null, when, null);
    }

    private static Trade buy(String price, int qty, String commission, String sebon, Instant when) {
        return new Trade(1L, 1L, TradeType.BUY, qty, new BigDecimal(price),
                commission == null ? null : new BigDecimal(commission),
                sebon == null ? null : new BigDecimal(sebon),
                null, null, when, null);
    }

    private static Trade sell(String price, int qty, String commission, String sebon,
                              String dp, Instant when) {
        return new Trade(1L, 1L, TradeType.SELL, qty, new BigDecimal(price),
                commission == null ? null : new BigDecimal(commission),
                sebon == null ? null : new BigDecimal(sebon),
                dp == null ? null : new BigDecimal(dp),
                null, when, null);
    }

    // ---- commission tiers -------------------------------------------------

    @Test
    void smallestTransactionsChargeTheFlatMinimum() {
        // Rs 10 * 50 shares = Rs 500 amount → Rs 10 flat minimum, not 0.36%*500 = 1.80
        assertEquals(0, new BigDecimal("10.00").compareTo(
                NepseCostEngine.commission(new BigDecimal("500"), RATES)));
        // The flat tier only applies up to Rs 2,500 of amount.
        assertEquals(0, new BigDecimal("10.00").compareTo(
                NepseCostEngine.commission(new BigDecimal("2500"), RATES)));
    }

    @Test
    void tierBoundariesSelectTheRightSlabs() {
        // exactly at a boundary the next percent tier takes over (2500 < amount <= 50000)
        assertEquals(0, new BigDecimal("180.00").compareTo(   // 0.36% of 50,000
                NepseCostEngine.commission(new BigDecimal("50000"), RATES)));
        // mid-band: 0.36% of 30,000 = 108
        assertEquals(0, new BigDecimal("108.00").compareTo(
                NepseCostEngine.commission(new BigDecimal("30000"), RATES)));
    }

    @Test
    void percentTiersChargePercentOfAmount() {
        // 0.36% of 10,000 = 36
        assertEquals(0, new BigDecimal("36.00").compareTo(
                NepseCostEngine.commission(new BigDecimal("10000"), RATES)));
        // 0.33% of 100,000 = 330
        assertEquals(0, new BigDecimal("330.00").compareTo(
                NepseCostEngine.commission(new BigDecimal("100000"), RATES)));
        // 0.31% of 1,000,000 = 3,100
        assertEquals(0, new BigDecimal("3100.00").compareTo(
                NepseCostEngine.commission(new BigDecimal("1000000"), RATES)));
        // 0.30% above 2M = 9,000 on 3M
        assertEquals(0, new BigDecimal("9000.00").compareTo(
                NepseCostEngine.commission(new BigDecimal("3000000"), RATES)));
    }

    @Test
    void sebonFeeAppliesToBothSides() {
        // 0.015% of 100,000 = 15
        assertEquals(0, new BigDecimal("15.00").compareTo(
                NepseCostEngine.sebonFee(new BigDecimal("100000"), RATES)));
        assertEquals(NepseCostEngine.sebonFee(new BigDecimal("500"), RATES),
                NepseCostEngine.sebonFee(new BigDecimal("500"), RATES));
    }

    // ---- free-entry types -------------------------------------------------

    @Test
    void allotmentTypesChargeNothing() {
        NepseCostEngine.BuyCosting ipo = NepseCostEngine.costBuy(
                TradeType.IPO, new BigDecimal("215"), 100, RATES);
        assertEquals(0, BigDecimal.ZERO.compareTo(ipo.commission()));
        assertEquals(0, BigDecimal.ZERO.compareTo(ipo.sebonFee()));
        assertEquals(0, new BigDecimal("21500").compareTo(ipo.totalCost()));

        NepseCostEngine.BuyCosting bonus = NepseCostEngine.costBuy(
                TradeType.BONUS, BigDecimal.ZERO, 10, RATES);
        assertEquals(0, BigDecimal.ZERO.compareTo(bonus.commission()));
        assertEquals(0, new BigDecimal("0").compareTo(bonus.totalCost()));
    }

    @Test
    void auctionIsPricedLikeABuy() {
        NepseCostEngine.BuyCosting buy = NepseCostEngine.costBuy(
                TradeType.BUY, new BigDecimal("100"), 1000, RATES);
        NepseCostEngine.BuyCosting auction = NepseCostEngine.costBuy(
                TradeType.AUCTION, new BigDecimal("100"), 1000, RATES);
        assertEquals(buy.commission(), auction.commission());
        assertEquals(buy.sebonFee(), auction.sebonFee());
    }

    // ---- CGT --------------------------------------------------------------

    @Test
    void cgtSlabsFollowTheHoldingPeriod() {
        assertEquals(0, new BigDecimal("5.0").compareTo(NepseCostEngine.cgtRate(365, RATES)));
        assertEquals(0, new BigDecimal("3.75").compareTo(NepseCostEngine.cgtRate(366, RATES)));
    }

    @Test
    void lossesAndNegativeRatesTaxNothing() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                NepseCostEngine.cgtTax(new BigDecimal("-500"), new BigDecimal("5.0"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                NepseCostEngine.cgtTax(BigDecimal.ZERO, new BigDecimal("5.0"))));
        // 5% of a 1,000 gain = 50
        assertEquals(0, new BigDecimal("50.00").compareTo(
                NepseCostEngine.cgtTax(new BigDecimal("1000"), new BigDecimal("5.0"))));
    }

    // ---- weighted average ledger ------------------------------------------

    @Test
    void weightedAverageMatchesTheActNotLotMatching() {
        Instant jan = Instant.parse("2026-01-05T04:00:00Z");
        Instant jun = Instant.parse("2026-06-05T04:00:00Z");
        Instant jul = Instant.parse("2026-07-05T04:00:00Z");

        NepseCostEngine.WacLedger ledger = new NepseCostEngine.WacLedger();
        ledger.addBuy(buy("100", 50, "35.55", "1.5", jan), RATES);   // cost 5037.05 → wac 100.7410
        ledger.addSell(sell("150", 50, "75.30", "11.25", "25", jun), RATES, ZONE);

        NepseCostEngine.WacLedger ledger2 = new NepseCostEngine.WacLedger();
        ledger2.addBuy(buy("100", 50, "35.55", "1.5", jan), RATES);
        NepseCostEngine.SellCosting sc = ledger2.addSell(
                sell("150", 50, "75.30", "11.25", "25", jun), RATES, ZONE);

        BigDecimal proceeds = new BigDecimal("7500").subtract(new BigDecimal("111.55"));
        assertEquals(0, proceeds.compareTo(sc.netProceeds()));
        assertEquals(0, new BigDecimal("100.7410").compareTo(sc.wacPerShare()));
        // gain = 7388.45 - 5037.05 = 2351.40 → 5% = 117.57
        assertEquals(0, new BigDecimal("117.57").compareTo(sc.cgt()));
        assertEquals(0, ledger2.openQuantity());
        assertEquals(0, new BigDecimal("5").compareTo(sc.cgtRate())); // < 365 days → 5%
    }

    @Test
    void bonusSharesDiluteTheAverage() {
        Instant jan = Instant.parse("2026-01-05T04:00:00Z");
        Instant now = Instant.parse("2026-04-05T04:00:00Z");

        NepseCostEngine.WacLedger ledger = new NepseCostEngine.WacLedger();
        ledger.addBuy(buy("400", 10, "72.56", "12", jan), RATES);   // 4084.56 → 408.456/sh
        ledger.addBuy(new Trade(1L, 1L, TradeType.BONUS, 10, BigDecimal.ZERO,
                null, null, null, null, now, "TDS noted"), RATES);

        assertEquals(20, ledger.openQuantity());
        // (4084.56 + 0) / 20 = 204.2280
        assertEquals(0, new BigDecimal("204.2280").compareTo(ledger.wacPerShare()));
    }

    @Test
    void sameDayBuyBeforeSellRaisesTheWac() {
        Instant day = Instant.parse("2026-04-05T04:00:00Z");
        Trade morningBuy = buy("200", 50, null, null, day);
        Trade afternoon = sell("210", 50, "37.8", "15.75", "25", day);

        NepseCostEngine.WacLedger ledger = new NepseCostEngine.WacLedger();
        ledger.addBuy(morningBuy, RATES);
        NepseCostEngine.SellCosting costing = ledger.addSell(afternoon, RATES, ZONE);

        // The same-day buy joined the pool before the sell consumed it.
        assertEquals(0, new BigDecimal("200.0000").compareTo(costing.wacPerShare()));
        assertEquals(0, ledger.openQuantity());
    }

    @Test
    void sameDaySellBeforeBuyKeepsTheOldWac() {
        Instant day = Instant.parse("2026-04-05T04:00:00Z");
        Instant older = Instant.parse("2026-01-05T04:00:00Z");

        NepseCostEngine.WacLedger ledger = new NepseCostEngine.WacLedger();
        ledger.addBuy(buy("100", 50, null, null, older), RATES);
        Trade first = sell("150", 50, "54", "22.5", "25", day);
        NepseCostEngine.SellCosting sellOne = ledger.addSell(first, RATES, ZONE);
        assertEquals(0, new BigDecimal("100.0000").compareTo(sellOne.wacPerShare()));

        // The afternoon buy of the same day raises nothing — position is closed.
        ledger.addBuy(buy("200", 50, null, null, day), RATES);
        assertEquals(0, new BigDecimal("200.0000").compareTo(ledger.wacPerShare()));
    }

    @Test
    void holdingPeriodComesFromTheEarliestRemainingLot() {
        Instant jan = Instant.parse("2025-01-05T04:00:00Z");
        Instant mar = Instant.parse("2025-03-05T04:00:00Z");
        Instant sale = Instant.parse("2026-03-05T04:00:00Z"); // ~424d after Jan, 365d after Mar

        NepseCostEngine.WacLedger ledger = new NepseCostEngine.WacLedger();
        ledger.addBuy(buy("100", 30, null, null, jan), RATES);
        ledger.addBuy(buy("120", 30, null, null, mar), RATES);

        // Sell only 30 (partial) — the WAC applies, but the pad is the earliest lot age.
        NepseCostEngine.SellCosting half = ledger.addSell(sell("150", 30, "54", "22.5", "25", sale), RATES, ZONE);
        int expected = NepseCostEngine.holdingDays(jan, sale, ZONE);
        assertEquals(expected, half.holdingDays());
        // still short slab? 424 > 365 → long
        assertEquals(0, new BigDecimal("3.75").compareTo(half.cgtRate()));

        // After the full position sells, the earliest lot resets on the next buy.
        NepseCostEngine.SellCosting rest = ledger.addSell(sell("150", 30, "54", "22.5", "25", sale), RATES, ZONE);
        assertEquals(0, new BigDecimal("110.0000").compareTo(rest.wacPerShare()));
        assertEquals(0, ledger.openQuantity());
        assertEquals(0, BigDecimal.ZERO.compareTo(ledger.wacPerShare()));
    }

    @Test
    void buyFeesCanBeExcludedFromTheBasis() {
        TradingRates excludeFees = new TradingRates(null, null, null, null, null, null, false);
        Instant when = Instant.parse("2026-01-05T04:00:00Z");

        NepseCostEngine.WacLedger ledger = new NepseCostEngine.WacLedger();
        ledger.addBuy(buy("100", 10, "36", "15", when), excludeFees);
        assertEquals(0, new BigDecimal("100.0000").compareTo(ledger.wacPerShare()));

        NepseCostEngine.WacLedger withFees = new NepseCostEngine.WacLedger();
        withFees.addBuy(buy("100", 10, "36", "15", when), RATES);
        assertEquals(0, new BigDecimal("105.1000").compareTo(withFees.wacPerShare()));
    }

    @Test
    void sellAggregateTotalsAccumulate() {
        Instant jan = Instant.parse("2026-01-05T04:00:00Z");
        Instant feb = Instant.parse("2026-02-05T04:00:00Z");

        NepseCostEngine.WacLedger ledger = new NepseCostEngine.WacLedger();
        ledger.addBuy(buy("100", 100, null, null, jan), RATES);
        NepseCostEngine.SellCosting one = ledger.addSell(
                sell("150", 50, "27", "11.25", "25", feb), RATES, ZONE);
        NepseCostEngine.SellCosting two = ledger.addSell(
                sell("150", 50, "27", "11.25", "25", feb), RATES, ZONE);

        assertEquals(0, ledger.openQuantity());
        assertEquals(0, one.cgt().add(two.cgt()).compareTo(ledger.totalTax()));
        assertEquals(0, one.netProfit().add(two.netProfit()).compareTo(ledger.totalNetProfit()));
    }

    @Test
    void chronologyCheckHelper() {
        Instant a = Instant.parse("2026-01-01T04:00:00Z");
        Instant b = a.plus(400, ChronoUnit.DAYS);
        assertTrue(NepseCostEngine.holdingDays(a, b, ZONE) > 365);
    }

    @Test
    void mergePrefersTheUpperLayer() {
        TradingRates base = TradingRates.DEFAULT;
        TradingRates patch = new TradingRates(null, new BigDecimal("0.02"),
                new BigDecimal("30"), null, null, 200, null);
        TradingRates merged = TradingRates.merge(base, patch);

        assertEquals(base.commissionTiers(), merged.commissionTiers());
        assertEquals(0, new BigDecimal("0.02").compareTo(merged.sebonFeePercent()));
        assertEquals(0, new BigDecimal("30").compareTo(merged.dpChargePerScripPerDay()));
        assertEquals(200, merged.cgtLongThresholdDays());
        assertEquals(base.buyFeesIncludedInBasis(), merged.buyFeesIncludedInBasis());
    }
}
