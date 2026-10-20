package com.nepse.portfolio.portfolio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FifoCalculatorTest {

    private final FifoCalculator calculator = new FifoCalculator();

    private Trade trade(TradeType type, int quantity, String price, String fees) {
        BigDecimal fee = new BigDecimal(fees);
        return new Trade(1L, 1L, type, quantity, new BigDecimal(price),
                fee, BigDecimal.ZERO, Instant.parse("2025-01-01T00:00:00Z"), null);
    }

    @Test
    void matchesTheWorkedExampleFromTheSpec() {
        List<Trade> trades = List.of(
                trade(TradeType.BUY, 10, "100", "0"),
                trade(TradeType.BUY, 5, "110", "0"),
                trade(TradeType.SELL, 12, "115", "0")
        );

        FifoCalculator.Result result = calculator.calculate(trades);

        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("160");
        assertThat(result.openQuantity()).isEqualTo(3);
        assertThat(result.openLots()).hasSize(1);
        assertThat(result.openLots().get(0).quantity()).isEqualTo(3);
        assertThat(result.openLots().get(0).costPerShare()).isEqualByComparingTo("110");
        assertThat(result.averageCost()).isEqualByComparingTo("110");
    }

    @Test
    void allocatesFeesPerShareOnBothSides() {
        List<Trade> trades = List.of(
                trade(TradeType.BUY, 10, "100", "10"),
                trade(TradeType.SELL, 10, "110", "10")
        );

        FifoCalculator.Result result = calculator.calculate(trades);

        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("80");
        assertThat(result.openQuantity()).isZero();
    }

    @Test
    void keepsRemainingLotsAfterPartialSell() {
        List<Trade> trades = List.of(
                trade(TradeType.BUY, 100, "200", "0"),
                trade(TradeType.BUY, 50, "220", "0"),
                trade(TradeType.SELL, 120, "240", "0")
        );

        FifoCalculator.Result result = calculator.calculate(trades);

        assertThat(result.openQuantity()).isEqualTo(30);
        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("4400");
        assertThat(result.averageCost()).isEqualByComparingTo("220");
    }

    @Test
    void computesRealizedLossWhenSellingBelowCost() {
        List<Trade> trades = List.of(
                trade(TradeType.BUY, 100, "500", "0"),
                trade(TradeType.SELL, 100, "450", "0")
        );

        FifoCalculator.Result result = calculator.calculate(trades);

        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("-5000");
        assertThat(result.openQuantity()).isZero();
    }
}
