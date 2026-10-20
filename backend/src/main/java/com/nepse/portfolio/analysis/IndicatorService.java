package com.nepse.portfolio.analysis;

import com.nepse.portfolio.market.PriceHistory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class IndicatorService {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    public Indicators compute(List<PriceHistory> history) {
        if (history == null || history.size() < 2) {
            return new Indicators(null, null, null, null, null, null, null, null,
                    "SIDEWAYS", null, null, null, null, null, history == null ? 0 : history.size());
        }

        List<PriceHistory> points = new ArrayList<>(history);
        points.sort(Comparator.comparing(PriceHistory::getTradeDate));

        List<Double> closes = points.stream().map(p -> p.getClosePrice().doubleValue()).toList();
        List<Double> volumes = points.stream().map(p -> (double) p.getVolume()).toList();
        int n = closes.size();
        double last = closes.get(n - 1);

        Double sma20 = sma(closes, 20);
        Double sma50 = sma(closes, 50);
        Double rsi = rsi(closes, 14);
        Double vol20 = average(volumes.subList(Math.max(0, n - 20), n));
        Double volumeRatio = vol20 == null || vol20 == 0 ? null : volumes.get(n - 1) / vol20;

        String trend;
        if (sma20 != null && sma50 != null) {
            if (last > sma20 && sma20 > sma50) {
                trend = "BULLISH";
            } else if (last < sma20 && sma20 < sma50) {
                trend = "BEARISH";
            } else {
                trend = "SIDEWAYS";
            }
        } else {
            trend = last > closes.get(0) ? "BULLISH" : "BEARISH";
        }

        int window = Math.min(20, n);
        List<PriceHistory> recent = points.subList(n - window, n);
        double support = recent.stream().mapToDouble(p -> p.getLowPrice().doubleValue()).min().orElse(last);
        double resistance = recent.stream().mapToDouble(p -> p.getHighPrice().doubleValue()).max().orElse(last);

        double high52 = points.stream().mapToDouble(p -> p.getHighPrice().doubleValue()).max().orElse(last);
        double low52 = points.stream().mapToDouble(p -> p.getLowPrice().doubleValue()).min().orElse(last);

        return new Indicators(
                dec(sma20),
                dec(sma50),
                dec(rsi),
                dec(pct(closes, 1)),
                dec(pct(closes, 7)),
                dec(pct(closes, 30)),
                dec(volatility(closes, 30)),
                dec(volumeRatio),
                trend,
                dec(support),
                dec(resistance),
                dec(high52),
                dec(low52),
                dec(vol20),
                n
        );
    }

    private Double sma(List<Double> values, int window) {
        if (values.size() < window) {
            return null;
        }
        double sum = 0;
        for (int i = values.size() - window; i < values.size(); i++) {
            sum += values.get(i);
        }
        return sum / window;
    }

    private Double average(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private Double rsi(List<Double> closes, int period) {
        if (closes.size() < period + 1) {
            return null;
        }
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            if (change > 0) {
                gain += change;
            } else {
                loss -= change;
            }
        }
        double avgGain = gain / period;
        double avgLoss = loss / period;

        for (int i = period + 1; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            double up = Math.max(change, 0);
            double down = Math.max(-change, 0);
            avgGain = (avgGain * (period - 1) + up) / period;
            avgLoss = (avgLoss * (period - 1) + down) / period;
        }

        if (avgLoss == 0) {
            return 100.0;
        }
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private Double volatility(List<Double> closes, int window) {
        int start = Math.max(1, closes.size() - window);
        List<Double> returns = new ArrayList<>();
        for (int i = start; i < closes.size(); i++) {
            double prev = closes.get(i - 1);
            if (prev != 0) {
                returns.add((closes.get(i) - prev) / prev);
            }
        }
        if (returns.size() < 2) {
            return null;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> (r - mean) * (r - mean))
                .sum() / (returns.size() - 1);
        return Math.sqrt(variance) * Math.sqrt(252) * 100;
    }

    private Double pct(List<Double> closes, int daysBack) {
        int index = closes.size() - 1 - daysBack;
        if (index < 0) {
            return null;
        }
        double base = closes.get(index);
        if (base == 0) {
            return null;
        }
        return (closes.get(closes.size() - 1) - base) / base * 100;
    }

    private BigDecimal dec(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
