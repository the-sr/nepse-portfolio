package com.nepse.portfolio.analysis;

import com.nepse.portfolio.analysis.AnalysisDtos.AverageSimulationRequest;
import com.nepse.portfolio.analysis.AnalysisDtos.AverageSimulationResponse;
import com.nepse.portfolio.analysis.AnalysisDtos.DcaOpportunityDto;
import com.nepse.portfolio.common.ApiException;
import com.nepse.portfolio.portfolio.PortfolioDtos.HoldingDto;
import com.nepse.portfolio.portfolio.PortfolioDtos.PortfolioDto;
import com.nepse.portfolio.portfolio.PortfolioService;
import com.nepse.portfolio.user.SettingsService;
import com.nepse.portfolio.user.UserSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class DcaService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PortfolioService portfolioService;
    private final SettingsService settingsService;

    public DcaService(PortfolioService portfolioService, SettingsService settingsService) {
        this.portfolioService = portfolioService;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public List<DcaOpportunityDto> opportunities(Long userId) {
        PortfolioDto portfolio = portfolioService.portfolio(userId);
        UserSettings settings = settingsService.loadOrCreate(userId);
        BigDecimal totalValue = portfolio.summary().currentValue();
        List<DcaOpportunityDto> result = new ArrayList<>();

        for (HoldingDto holding : portfolio.holdings()) {
            BigDecimal loss = holding.unrealizedPercent();
            if (loss == null || loss.compareTo(settings.getLossThresholdMinor().negate()) > 0) {
                continue;
            }

            String level;
            BigDecimal multiplier;
            if (loss.compareTo(settings.getLossThresholdAggressive().negate()) <= 0) {
                level = "AGGRESSIVE";
                multiplier = new BigDecimal("2.5");
            } else if (loss.compareTo(settings.getLossThresholdMajor().negate()) <= 0) {
                level = "MAJOR";
                multiplier = new BigDecimal("2.0");
            } else {
                level = "MINOR";
                multiplier = new BigDecimal("1.5");
            }

            BigDecimal budget = settings.getBaseBuyAmount().multiply(multiplier);
            budget = budget.min(settings.getMaxCapitalPerStock());

            BigDecimal positionCap = totalValue.multiply(settings.getMaxPositionPercent())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal room = positionCap.subtract(holding.currentValue());
            if (room.signum() <= 0) {
                continue;
            }
            budget = budget.min(room);

            BigDecimal price = holding.ltp();
            if (price.signum() <= 0) {
                continue;
            }
            int suggestedQuantity = budget.divide(price, 0, RoundingMode.FLOOR).intValue();
            if (suggestedQuantity < 1) {
                continue;
            }

            BigDecimal addedCapital = price.multiply(BigDecimal.valueOf(suggestedQuantity));
            BigDecimal newAverage = weightedAverage(holding.quantity(), holding.averageCost(),
                    suggestedQuantity, price);
            BigDecimal improvement = holding.averageCost().signum() == 0 ? BigDecimal.ZERO
                    : holding.averageCost().subtract(newAverage)
                    .multiply(HUNDRED)
                    .divide(holding.averageCost(), 2, RoundingMode.HALF_UP);

            result.add(new DcaOpportunityDto(
                    holding.symbol(),
                    holding.companyName(),
                    holding.quantity(),
                    holding.averageCost(),
                    price,
                    loss,
                    level,
                    suggestedQuantity,
                    addedCapital,
                    newAverage,
                    improvement,
                    String.format("%s position is %.2f%% below cost; a %s tranche lowers the average to रु %s",
                            holding.symbol(), loss.abs(), level.toLowerCase(), newAverage.toPlainString())
            ));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public AverageSimulationResponse simulate(Long userId, AverageSimulationRequest request) {
        HoldingDto holding = portfolioService.holdings(userId).stream()
                .filter(h -> h.symbol().equalsIgnoreCase(request.symbol()))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("You do not hold " + request.symbol()));

        BigDecimal price = request.price() == null ? holding.ltp() : request.price();
        if (price.signum() <= 0) {
            throw ApiException.badRequest("Price must be greater than zero");
        }

        int addedQuantity;
        String message;
        boolean feasible = true;

        switch (request.mode()) {
            case "QTY" -> {
                if (request.quantity() == null || request.quantity() < 1) {
                    throw ApiException.badRequest("Quantity is required for QTY mode");
                }
                addedQuantity = request.quantity();
                message = "Buying " + addedQuantity + " shares at रु " + price;
            }
            case "BUDGET" -> {
                if (request.budget() == null || request.budget().signum() <= 0) {
                    throw ApiException.badRequest("Budget is required for BUDGET mode");
                }
                addedQuantity = request.budget().divide(price, 0, RoundingMode.FLOOR).intValue();
                if (addedQuantity < 1) {
                    feasible = false;
                    message = "Budget is too small to buy a single share at this price";
                } else {
                    message = "Budget of रु " + request.budget() + " buys " + addedQuantity + " shares";
                }
            }
            case "TARGET" -> {
                if (request.targetAverage() == null) {
                    throw ApiException.badRequest("Target average is required for TARGET mode");
                }
                BigDecimal target = request.targetAverage();
                if (target.compareTo(holding.averageCost()) >= 0) {
                    feasible = false;
                    addedQuantity = 0;
                    message = "Target average must be below your current average cost";
                } else if (target.compareTo(price) <= 0) {
                    feasible = false;
                    addedQuantity = 0;
                    message = "The buy price must be below the target average for averaging down to work";
                } else {
                    BigDecimal numerator = BigDecimal.valueOf(holding.quantity())
                            .multiply(holding.averageCost().subtract(target));
                    BigDecimal denominator = target.subtract(price);
                    addedQuantity = numerator.divide(denominator, 0, RoundingMode.CEILING).intValue();
                    message = "You need " + addedQuantity + " shares at रु " + price + " to reach रु " + target;
                }
            }
            default -> throw ApiException.badRequest("Unknown simulation mode");
        }

        BigDecimal addedCapital = price.multiply(BigDecimal.valueOf(addedQuantity));
        int newQuantity = holding.quantity() + addedQuantity;
        BigDecimal newAverage = newQuantity == 0 ? holding.averageCost()
                : weightedAverage(holding.quantity(), holding.averageCost(), addedQuantity, price);
        BigDecimal newInvestment = holding.invested().add(addedCapital);
        BigDecimal reduction = holding.averageCost().signum() == 0 ? BigDecimal.ZERO
                : holding.averageCost().subtract(newAverage)
                .multiply(HUNDRED)
                .divide(holding.averageCost(), 2, RoundingMode.HALF_UP);

        return new AverageSimulationResponse(
                holding.symbol(),
                holding.quantity(),
                holding.averageCost(),
                price,
                addedQuantity,
                addedCapital.setScale(2, RoundingMode.HALF_UP),
                newQuantity,
                newAverage,
                newInvestment.setScale(2, RoundingMode.HALF_UP),
                reduction,
                feasible,
                message
        );
    }

    private BigDecimal weightedAverage(int quantity, BigDecimal averageCost, int addedQuantity,
                                       BigDecimal price) {
        int total = quantity + addedQuantity;
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal current = averageCost.multiply(BigDecimal.valueOf(quantity));
        BigDecimal added = price.multiply(BigDecimal.valueOf(addedQuantity));
        return current.add(added).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
