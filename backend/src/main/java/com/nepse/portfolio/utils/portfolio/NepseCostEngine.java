package com.nepse.portfolio.utils.portfolio;

import com.nepse.portfolio.domain.Trade;
import com.nepse.portfolio.domain.TradeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Nepal-market cost & tax engine (docs/nepse-costs.md).
 *
 * <p>Everything here is a pure function of (rates, prices, quantities). Fees
 * for free-entry types (IPO/FPO/RIGHT/BONUS) are zero, the commission tier
 * covers broker+NEPSE+SEBON's bundled share, SEBON's 0.015% transaction fee is
 * separate, and capital gains tax is computed on a weighted-average cost basis
 * as Income Tax Act 2058 section 95क(2) requires.</p>
 */
public final class NepseCostEngine {

    private NepseCostEngine() {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    public static final int CGT_SHORT_MAX_DAYS = 365;

    public record BuyCosting(BigDecimal commission, BigDecimal sebonFee, BigDecimal totalCost) {
        public BigDecimal fees() {
            return commission.add(sebonFee);
        }
    }

    public record SellCosting(BigDecimal grossAmount, BigDecimal commission, BigDecimal sebonFee,
                              BigDecimal dpCharge, BigDecimal netProceeds, BigDecimal cgt,
                              BigDecimal netProfit, BigDecimal wacPerShare, int holdingDays,
                              BigDecimal cgtRate) {
        public BigDecimal fees() {
            return commission.add(sebonFee).add(dpCharge);
        }
    }

    /** Tiered total commission: matches the amount to a slab, then charges the
     *  greater of the slab's percent-of-amount and its flat minimum. */
    public static BigDecimal commission(BigDecimal amount, TradingRates rates) {
        List<TradingRates.RateTier> tiers = rates.commissionTiers();
        if (tiers == null || tiers.isEmpty()) {
            return BigDecimal.ZERO;
        }
        TradingRates.RateTier found = null;
        for (TradingRates.RateTier tier : tiers) {
            if (tier.upTo() == null || amount.compareTo(tier.upTo()) <= 0) {
                found = tier;
                break;
            }
        }
        if (found == null) {
            found = tiers.get(tiers.size() - 1);
        }
        BigDecimal percent = found.percent() == null
                ? BigDecimal.ZERO
                : amount.multiply(found.percent()).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        BigDecimal flat = found.flat() == null ? BigDecimal.ZERO : found.flat();
        BigDecimal charge = percent.max(flat).setScale(2, RoundingMode.CEILING);
        return charge;
    }

    public static BigDecimal sebonFee(BigDecimal amount, TradingRates rates) {
        BigDecimal percent = rates.sebonFeePercent();
        if (percent == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(percent).divide(HUNDRED, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.CEILING);
    }

    public static boolean chargesEntryFees(TradeType type) {
        return type == TradeType.BUY || type == TradeType.AUCTION;
    }

    /** Free-entry types (IPO/FPO/RIGHT/BONUS) produce zero legitimate fees. */
    public static boolean isFreeEntry(TradeType type) {
        return type == TradeType.IPO || type == TradeType.FPO
                || type == TradeType.RIGHT || type == TradeType.BONUS;
    }

    public static boolean increasesQuantity(TradeType type) {
        return type != TradeType.SELL;
    }

    public static BuyCosting costBuy(TradeType type, BigDecimal price, int quantity, TradingRates rates) {
        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
        if (!chargesEntryFees(type)) {
            return new BuyCosting(BigDecimal.ZERO, BigDecimal.ZERO, amount);
        }
        BigDecimal commission = commission(amount, rates);
        BigDecimal sebon = sebonFee(amount, rates);
        return new BuyCosting(commission, sebon, amount.add(commission).add(sebon));
    }

    /**
     * CGT slab by holding period: <= threshold days uses the short rate, above
     * it the long rate. Non-positive gains are not taxed.
     */
    public static BigDecimal cgtRate(int holdingDays, TradingRates rates) {
        int threshold = rates.cgtLongThresholdDays() == null ? CGT_SHORT_MAX_DAYS : rates.cgtLongThresholdDays();
        BigDecimal rate = holdingDays <= threshold ? rates.cgtShortRate() : rates.cgtLongRate();
        return rate == null ? BigDecimal.ZERO : rate;
    }

    public static BigDecimal cgtTax(BigDecimal gain, BigDecimal rate) {
        if (gain.signum() <= 0 || rate.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return gain.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public static int holdingDays(Instant acquired, Instant sold, ZoneId zone) {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.ofInstant(acquired, zone), LocalDate.ofInstant(sold, zone));
    }

    public record SellFeesInput(BigDecimal price, int quantity, BigDecimal brokerage,
                                BigDecimal sebonFee, BigDecimal dpCharge) {
    }

    /** Sell-side economics for one trade given its snapshots (CGT excluded; it
     *  is levied on the gain separately). */
    public static BigDecimal netSellPerShare(Trade trade) {
        BigDecimal fees = trade.getBrokerage().add(trade.getSebonFee()).add(trade.getDpCharge());
        return trade.getPrice().subtract(
                fees.divide(BigDecimal.valueOf(trade.getQuantity()), 8, RoundingMode.HALF_UP));
    }

    /**
     * Weighted-average-cost ledger for one scrip, following Income Tax Act
     * 2058 §95क(2). Trades must be ordered by (tradeDate, id). Free-entry lots
     * join at their price (bonus: Rs 0); fee-charging lots join at
     * price + fees/qty; every SELL costs the current WAC and is taxed by the
     * holding period of the earliest remaining lot.
     */
    public static final class WacLedger {
        private int quantity;
        private BigDecimal totalCost = BigDecimal.ZERO;
        private Instant earliestLot = null;
        private BigDecimal totalTax = BigDecimal.ZERO;
        private BigDecimal totalNetProfit = BigDecimal.ZERO;

        public void addBuy(Trade trade, TradingRates rates) {
            BigDecimal amount = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
            boolean includeFees = rates.buyFeesIncludedInBasis() == null || rates.buyFeesIncludedInBasis();
            BigDecimal cost = amount;
            if (includeFees) {
                cost = amount.add(trade.getBrokerage()).add(trade.getSebonFee());
            }
            totalCost = totalCost.add(cost);
            quantity += trade.getQuantity();
            if (earliestLot == null || trade.getTradeDate().isBefore(earliestLot)) {
                earliestLot = trade.getTradeDate();
            }
        }

        public SellCosting addSell(Trade trade, TradingRates rates, ZoneId zone) {
            int qty = trade.getQuantity();
            BigDecimal wac = wacPerShare();
            BigDecimal gross = trade.getPrice().multiply(BigDecimal.valueOf(qty));
            BigDecimal fees = trade.getBrokerage().add(trade.getSebonFee()).add(trade.getDpCharge());
            BigDecimal proceeds = gross.subtract(fees);
            BigDecimal cost = wac.multiply(BigDecimal.valueOf(qty));
            BigDecimal gain = proceeds.subtract(cost);
            int holding = earliestLot == null ? 0
                    : holdingDays(earliestLot, trade.getTradeDate(), zone);
            BigDecimal rate = cgtRate(holding, rates);
            BigDecimal tax = cgtTax(gain, rate);
            BigDecimal netProfit = gain.subtract(tax);

            totalCost = totalCost.subtract(cost);
            quantity -= qty;
            totalTax = totalTax.add(tax);
            totalNetProfit = totalNetProfit.add(netProfit);

            return new SellCosting(gross, trade.getBrokerage(), trade.getSebonFee(),
                    trade.getDpCharge(), proceeds, tax, netProfit, wac, holding, rate);
        }

        public BigDecimal wacPerShare() {
            return quantity <= 0
                    ? BigDecimal.ZERO
                    : totalCost.divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP);
        }

        public int openQuantity() {
            return quantity;
        }

        public BigDecimal wacCost() {
            return wacPerShare().multiply(BigDecimal.valueOf(quantity));
        }

        public Instant earliestLotDate() {
            return earliestLot;
        }

        public BigDecimal totalTax() {
            return totalTax.setScale(2, RoundingMode.HALF_UP);
        }

        public BigDecimal totalNetProfit() {
            return totalNetProfit.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
