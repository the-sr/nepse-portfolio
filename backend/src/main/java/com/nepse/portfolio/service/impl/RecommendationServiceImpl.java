package com.nepse.portfolio.service.impl;

import com.nepse.portfolio.service.RecommendationService;
import com.nepse.portfolio.domain.PriceHistory;
import com.nepse.portfolio.dto.analysis.Indicators;
import com.nepse.portfolio.service.IndicatorService;

import com.nepse.portfolio.dto.analysis.AnalysisDtos.RecommendationDto;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.StockAnalysisDto;
import com.nepse.portfolio.common.ApiException;
import com.nepse.portfolio.dto.market.MarketDtos.StockDto;
import com.nepse.portfolio.repository.PriceHistoryRepository;
import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.repository.StockRepository;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.HoldingDto;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.PortfolioDto;
import com.nepse.portfolio.service.PortfolioService;
import com.nepse.portfolio.domain.Trade;
import com.nepse.portfolio.repository.TradeRepository;
import com.nepse.portfolio.utils.MlClient;
import com.nepse.portfolio.service.SettingsService;
import com.nepse.portfolio.domain.UserSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final Set<String> ACTIONS =
            Set.of("STRONG_BUY", "BUY", "HOLD", "SELL", "STRONG_SELL");

    private final PortfolioService portfolioService;
    private final StockRepository stockRepository;
    private final PriceHistoryRepository historyRepository;
    private final TradeRepository tradeRepository;
    private final IndicatorService indicatorService;
    private final SettingsService settingsService;
    private final MlClient mlClient;
    private final double mlMinConfidence;

    public RecommendationServiceImpl(PortfolioService portfolioService,
                                 StockRepository stockRepository,
                                 PriceHistoryRepository historyRepository,
                                 TradeRepository tradeRepository,
                                 IndicatorService indicatorService,
                                 SettingsService settingsService,
                                 MlClient mlClient,
                                 @Value("${app.ml.min-confidence:0.35}") double mlMinConfidence) {
        this.portfolioService = portfolioService;
        this.stockRepository = stockRepository;
        this.historyRepository = historyRepository;
        this.tradeRepository = tradeRepository;
        this.indicatorService = indicatorService;
        this.settingsService = settingsService;
        this.mlClient = mlClient;
        this.mlMinConfidence = mlMinConfidence;
    }

    @Transactional(readOnly = true)
    public List<RecommendationDto> recommendations(Long userId) {
        PortfolioDto portfolio = portfolioService.portfolio(userId);
        UserSettings settings = settingsService.loadOrCreate(userId);
        BigDecimal totalValue = portfolio.summary().currentValue();

        return portfolio.holdings().stream()
                .map(holding -> {
                    Stock stock = stockRepository.findById(holding.stockId()).orElse(null);
                    if (stock == null) {
                        return null;
                    }
                    Indicators indicators = indicatorsFor(stock);
                    int holdingDays = holdingDays(userId, stock.getId());
                    return recommend(stock, indicators, holding, settings, totalValue, holdingDays);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public StockAnalysisDto analyse(Long userId, String symbol) {
        Stock stock = stockRepository.findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> ApiException.notFound("Stock " + symbol + " not found"));
        Indicators indicators = indicatorsFor(stock);
        PortfolioDto portfolio = portfolioService.portfolio(userId);
        UserSettings settings = settingsService.loadOrCreate(userId);
        HoldingDto holding = portfolio.holdings().stream()
                .filter(h -> h.stockId().equals(stock.getId()))
                .findFirst()
                .orElse(null);

        RecommendationDto recommendation = recommend(stock, indicators, holding, settings,
                portfolio.summary().currentValue(), holdingDays(userId, stock.getId()));
        return new StockAnalysisDto(StockDto.from(stock), indicators, recommendation);
    }

    public Indicators indicatorsFor(Stock stock) {
        List<com.nepse.portfolio.domain.PriceHistory> history =
                historyRepository.findTop400ByStockIdOrderByTradeDateDesc(stock.getId());
        return indicatorService.compute(history);
    }

    private RecommendationDto recommend(Stock stock, Indicators indicators, HoldingDto holding,
                                        UserSettings settings, BigDecimal portfolioValue,
                                        int holdingDays) {
        RuleResult rule = score(stock, indicators, holding, settings, portfolioValue);
        String action = rule.action;
        BigDecimal confidence = rule.confidence;
        String source = "RULES";
        List<String> reasons = new ArrayList<>(rule.reasons);

        if (mlClient.isEnabled()) {
            BigDecimal lossPercent = holding == null ? BigDecimal.ZERO : holding.unrealizedPercent();
            BigDecimal concentration = holding == null || portfolioValue.signum() == 0
                    ? BigDecimal.ZERO
                    : holding.currentValue().multiply(BigDecimal.valueOf(100))
                    .divide(portfolioValue, 2, RoundingMode.HALF_UP);

            Optional<MlClient.Prediction> prediction =
                    mlClient.predict(stock.getSymbol(), lossPercent, holdingDays, concentration);

            if (prediction.isPresent()) {
                MlClient.Prediction ml = prediction.get();
                String mlAction = ml.action() == null ? "" : ml.action().toUpperCase(Locale.ROOT);
                if (ACTIONS.contains(mlAction) && ml.confidence() >= mlMinConfidence) {
                    action = mlAction;
                    confidence = BigDecimal.valueOf(Math.max(0, Math.min(1, ml.confidence())) * 100)
                            .setScale(2, RoundingMode.HALF_UP);
                    source = "ML";
                    if (ml.reasons() != null && !ml.reasons().isEmpty()) {
                        reasons = new ArrayList<>(ml.reasons());
                    }
                    if (ml.modelVersion() != null) {
                        reasons.add("Model version: " + ml.modelVersion());
                    }
                } else if (ACTIONS.contains(mlAction)) {
                    reasons.add(String.format(
                            "ML confidence was %.0f%%, below the %.0f%% threshold, so the rule score is shown",
                            ml.confidence() * 100, mlMinConfidence * 100));
                }
            }
        }

        Integer quantity = holding == null ? null : holding.quantity();
        BigDecimal avgCost = holding == null ? null : holding.averageCost();
        BigDecimal pl = holding == null ? null : holding.unrealizedProfitLoss();
        BigDecimal plPercent = holding == null ? null : holding.unrealizedPercent();

        return new RecommendationDto(
                stock.getSymbol(),
                stock.getCompanyName(),
                stock.getLtp(),
                stock.getChangePercent(),
                action,
                rule.score,
                confidence,
                source,
                reasons,
                indicators,
                quantity,
                avgCost,
                pl,
                plPercent,
                Instant.now()
        );
    }

    private RuleResult score(Stock stock, Indicators indicators, HoldingDto holding,
                             UserSettings settings, BigDecimal portfolioValue) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        switch (indicators.trend()) {
            case "BULLISH" -> {
                score += 30;
                reasons.add("Price is above the 20-day and 50-day moving averages (bullish trend)");
            }
            case "BEARISH" -> {
                score -= 30;
                reasons.add("Price is below the 20-day and 50-day moving averages (bearish trend)");
            }
            default -> reasons.add("Moving averages are flat, so the trend is unclear");
        }

        if (indicators.rsi14() != null) {
            double rsi = indicators.rsi14().doubleValue();
            if (rsi < 30) {
                score += 20;
                reasons.add(String.format("RSI is %.1f (oversold), which often precedes a bounce", rsi));
            } else if (rsi < 45) {
                score += 10;
                reasons.add(String.format("RSI is %.1f, leaning oversold", rsi));
            } else if (rsi > 70) {
                score -= 20;
                reasons.add(String.format("RSI is %.1f (overbought), raising pullback risk", rsi));
            } else if (rsi > 60) {
                score -= 5;
                reasons.add(String.format("RSI is %.1f, getting warm", rsi));
            }
        }

        if (indicators.return7d() != null) {
            double r7 = indicators.return7d().doubleValue();
            if (r7 > 3) {
                score += 12;
                reasons.add(String.format("7-day momentum is +%.2f%%", r7));
            } else if (r7 < -3) {
                score -= 12;
                reasons.add(String.format("7-day momentum is %.2f%%", r7));
            }
        }
        if (indicators.return30d() != null) {
            double r30 = indicators.return30d().doubleValue();
            if (r30 > 8) {
                score += 8;
            } else if (r30 < -8) {
                score -= 8;
                reasons.add(String.format("30-day momentum is %.2f%%", r30));
            }
        }

        if (indicators.volumeRatio() != null && indicators.volumeRatio().doubleValue() > 1.5) {
            if (indicators.return1d() != null && indicators.return1d().doubleValue() >= 0) {
                score += 5;
                reasons.add("Above-average volume is confirming the move");
            } else {
                score -= 5;
                reasons.add("Heavy volume on a down day suggests distribution");
            }
        }

        if (holding != null) {
            BigDecimal loss = holding.unrealizedPercent();
            if (loss != null) {
                if (loss.compareTo(settings.getLossThresholdAggressive().negate()) <= 0) {
                    score += 15;
                    reasons.add("Position is deep in loss; averaging opportunities are flagged separately");
                } else if (loss.compareTo(settings.getLossThresholdMinor().negate()) <= 0) {
                    score += 8;
                    reasons.add("Position is in loss, which supports a staged averaging approach");
                } else if (loss.compareTo(BigDecimal.valueOf(25)) >= 0) {
                    score -= 10;
                    reasons.add("Position is up more than 25%; consider protecting gains");
                }
            }

            if (portfolioValue.signum() > 0) {
                BigDecimal weight = holding.currentValue().multiply(BigDecimal.valueOf(100))
                        .divide(portfolioValue, 2, RoundingMode.HALF_UP);
                if (weight.compareTo(settings.getMaxPositionPercent()) > 0) {
                    score -= 10;
                    reasons.add(String.format("Position is %.1f%% of the portfolio, above your %.1f%% cap",
                            weight, settings.getMaxPositionPercent()));
                }
            }
        }

        if (indicators.rsi14() != null && holding != null && holding.unrealizedPercent() != null
                && holding.unrealizedPercent().compareTo(BigDecimal.valueOf(25)) >= 0
                && indicators.rsi14().doubleValue() > 70) {
            score -= 15;
            reasons.add("Profitable and overbought: a partial exit is reasonable");
        }

        score = Math.max(-100, Math.min(100, score));
        String action;
        if (score >= 50) {
            action = "STRONG_BUY";
        } else if (score >= 20) {
            action = "BUY";
        } else if (score > -20) {
            action = "HOLD";
        } else if (score > -50) {
            action = "SELL";
        } else {
            action = "STRONG_SELL";
        }

        if (reasons.isEmpty()) {
            reasons.add("No strong technical signal; holding is reasonable");
        }

        BigDecimal confidence = BigDecimal.valueOf(Math.min(95, 52 + Math.abs(score) * 0.45))
                .setScale(2, RoundingMode.HALF_UP);
        return new RuleResult(action, score, confidence, reasons);
    }

    private int holdingDays(Long userId, Long stockId) {
        List<Trade> trades = tradeRepository
                .findByUserIdAndStockIdOrderByTradeDateAscIdAsc(userId, stockId);
        if (trades.isEmpty()) {
            return 0;
        }
        Instant first = trades.get(0).getTradeDate();
        long days = Duration.between(first, Instant.now()).toDays();
        return (int) Math.max(0, Math.min(days, 3650));
    }

    private record RuleResult(String action, int score, BigDecimal confidence, List<String> reasons) {
    }
}
