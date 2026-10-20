package com.nepse.portfolio.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * FIFO cost-basis engine. A sell consumes the oldest open lots first. Fees are
 * allocated per share on both sides so realized and unrealized P&L stay consistent.
 */
public class FifoCalculator {

    public record Lot(int quantity, BigDecimal costPerShare, Instant acquiredAt) {
    }

    public record RealizedSale(
            int quantity,
            BigDecimal costPerShare,
            BigDecimal sellPricePerShare,
            Instant acquiredAt,
            Instant soldAt,
            BigDecimal profitLoss
    ) {
    }

    public record Result(List<Lot> openLots, BigDecimal realizedProfitLoss, List<RealizedSale> sales) {

        public int openQuantity() {
            return openLots.stream().mapToInt(Lot::quantity).sum();
        }

        public BigDecimal invested() {
            return openLots.stream()
                    .map(lot -> lot.costPerShare().multiply(BigDecimal.valueOf(lot.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal averageCost() {
            int quantity = openQuantity();
            if (quantity == 0) {
                return BigDecimal.ZERO;
            }
            return invested().divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP);
        }
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Result calculate(List<Trade> trades) {
        Deque<MutableLot> lots = new ArrayDeque<>();
        List<RealizedSale> sales = new ArrayList<>();
        BigDecimal realized = BigDecimal.ZERO;

        for (Trade trade : trades) {
            int quantity = trade.getQuantity();
            if (quantity <= 0) {
                continue;
            }
            BigDecimal feesPerShare = trade.getFees()
                    .divide(BigDecimal.valueOf(quantity), 8, RoundingMode.HALF_UP);

            if (trade.getType() == TradeType.BUY) {
                BigDecimal costPerShare = trade.getPrice().add(feesPerShare);
                lots.addLast(new MutableLot(quantity, costPerShare, trade.getTradeDate()));
                continue;
            }

            BigDecimal netSellPerShare = trade.getPrice().subtract(feesPerShare);
            int remaining = quantity;
            while (remaining > 0 && !lots.isEmpty()) {
                MutableLot lot = lots.peekFirst();
                int matched = Math.min(remaining, lot.quantity);
                BigDecimal profit = netSellPerShare.subtract(lot.costPerShare)
                        .multiply(BigDecimal.valueOf(matched));
                realized = realized.add(profit);
                sales.add(new RealizedSale(matched, lot.costPerShare, netSellPerShare,
                        lot.acquiredAt, trade.getTradeDate(), profit));
                lot.quantity -= matched;
                remaining -= matched;
                if (lot.quantity == 0) {
                    lots.removeFirst();
                }
            }
            // Any unmatched remainder is invalid input; trade creation rejects overselling.
        }

        List<Lot> openLots = lots.stream()
                .map(lot -> new Lot(lot.quantity, lot.costPerShare, lot.acquiredAt))
                .toList();

        return new Result(openLots, realized.setScale(2, RoundingMode.HALF_UP), List.copyOf(sales));
    }

    private static final class MutableLot {
        private int quantity;
        private final BigDecimal costPerShare;
        private final Instant acquiredAt;

        private MutableLot(int quantity, BigDecimal costPerShare, Instant acquiredAt) {
            this.quantity = quantity;
            this.costPerShare = costPerShare;
            this.acquiredAt = acquiredAt;
        }
    }
}
