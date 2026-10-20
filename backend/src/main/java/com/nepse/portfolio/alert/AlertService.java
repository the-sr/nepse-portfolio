package com.nepse.portfolio.alert;

import com.nepse.portfolio.alert.AlertDtos.AlertDto;
import com.nepse.portfolio.alert.AlertDtos.AlertEventDto;
import com.nepse.portfolio.alert.AlertDtos.CreateAlertRequest;
import com.nepse.portfolio.alert.AlertDtos.UpdateAlertRequest;
import com.nepse.portfolio.common.ApiException;
import com.nepse.portfolio.market.Stock;
import com.nepse.portfolio.market.StockRepository;
import com.nepse.portfolio.stream.StreamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertEventRepository eventRepository;
    private final StockRepository stockRepository;
    private final StreamService streamService;

    public AlertService(AlertRepository alertRepository,
                        AlertEventRepository eventRepository,
                        StockRepository stockRepository,
                        StreamService streamService) {
        this.alertRepository = alertRepository;
        this.eventRepository = eventRepository;
        this.stockRepository = stockRepository;
        this.streamService = streamService;
    }

    @Transactional(readOnly = true)
    public List<AlertDto> list(Long userId) {
        List<Alert> alerts = alertRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Stock> stocks = stocks(alerts);
        return alerts.stream().map(alert -> toDto(alert, stocks.get(alert.getStockId()))).toList();
    }

    @Transactional
    public AlertDto create(Long userId, CreateAlertRequest request) {
        Stock stock = stockRepository.findBySymbolIgnoreCase(request.symbol().trim())
                .orElseThrow(() -> ApiException.notFound("Stock " + request.symbol() + " not found"));

        if (!stock.isActive()) {
            throw ApiException.badRequest(stock.getSymbol()
                    + " is no longer listed as active on NEPSE, so new alerts are blocked");
        }

        validateThreshold(request.type(), request.threshold());
        Alert alert = alertRepository.save(new Alert(userId, stock.getId(), request.type(), request.threshold()));
        return toDto(alert, stock);
    }

    @Transactional
    public AlertDto update(Long userId, Long alertId, UpdateAlertRequest request) {
        Alert alert = alertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> ApiException.notFound("Alert not found"));
        if (request.threshold() != null) {
            validateThreshold(alert.getType(), request.threshold());
            alert.setThreshold(request.threshold());
            alert.rearm();
        }
        if (request.active() != null) {
            alert.setActive(request.active());
            if (request.active()) {
                alert.rearm();
            }
        }
        Stock stock = stockRepository.findById(alert.getStockId()).orElse(null);
        return toDto(alertRepository.save(alert), stock);
    }

    @Transactional
    public void delete(Long userId, Long alertId) {
        Alert alert = alertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> ApiException.notFound("Alert not found"));
        alertRepository.delete(alert);
    }

    @Transactional(readOnly = true)
    public List<AlertEventDto> events(Long userId) {
        List<AlertEvent> events = eventRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
        List<Long> stockIds = events.stream().map(AlertEvent::getStockId).distinct().toList();
        Map<Long, Stock> stocks = stockRepository.findAllById(stockIds).stream()
                .collect(Collectors.toMap(Stock::getId, Function.identity()));
        return events.stream()
                .map(event -> new AlertEventDto(
                        event.getId(),
                        event.getAlertId(),
                        stocks.containsKey(event.getStockId()) ? stocks.get(event.getStockId()).getSymbol() : "?",
                        event.getPrice(),
                        event.getMessage(),
                        event.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void evaluate(List<Stock> updatedStocks) {
        if (updatedStocks.isEmpty()) {
            return;
        }
        List<Long> ids = updatedStocks.stream().map(Stock::getId).toList();
        Map<Long, Stock> byId = updatedStocks.stream()
                .collect(Collectors.toMap(Stock::getId, Function.identity()));

        for (Alert alert : alertRepository.findByActiveTrueAndStockIdIn(ids)) {
            if (alert.isTriggered()) {
                continue;
            }
            Stock stock = byId.get(alert.getStockId());
            if (stock == null) {
                continue;
            }
            String message = match(alert, stock);
            if (message == null) {
                continue;
            }
            alert.markTriggered();
            alertRepository.save(alert);
            AlertEvent event = eventRepository.save(
                    new AlertEvent(alert.getId(), alert.getUserId(), stock.getId(), stock.getLtp(), message));
            streamService.sendAlert(alert.getUserId(), new AlertEventDto(
                    event.getId(), event.getAlertId(), stock.getSymbol(), event.getPrice(),
                    event.getMessage(), event.getCreatedAt()));
        }
    }

    private String match(Alert alert, Stock stock) {
        BigDecimal ltp = stock.getLtp();
        switch (alert.getType()) {
            case ABOVE -> {
                if (ltp.compareTo(alert.getThreshold()) >= 0) {
                    return String.format("%s rose above रु %s (LTP रु %s)",
                            stock.getSymbol(), alert.getThreshold().toPlainString(), ltp.toPlainString());
                }
            }
            case BELOW -> {
                if (ltp.compareTo(alert.getThreshold()) <= 0) {
                    return String.format("%s fell below रु %s (LTP रु %s)",
                            stock.getSymbol(), alert.getThreshold().toPlainString(), ltp.toPlainString());
                }
            }
            case PERCENT_CHANGE -> {
                if (stock.getChangePercent().abs().compareTo(alert.getThreshold()) >= 0) {
                    return String.format("%s moved %s%% today (LTP रु %s)",
                            stock.getSymbol(), stock.getChangePercent().toPlainString(), ltp.toPlainString());
                }
            }
        }
        return null;
    }

    private void validateThreshold(AlertType type, BigDecimal threshold) {
        if (type == AlertType.PERCENT_CHANGE && threshold.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest("Percentage threshold cannot exceed 100");
        }
    }

    private Map<Long, Stock> stocks(List<Alert> alerts) {
        List<Long> ids = alerts.stream().map(Alert::getStockId).distinct().toList();
        return stockRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Stock::getId, Function.identity()));
    }

    private AlertDto toDto(Alert alert, Stock stock) {
        return new AlertDto(
                alert.getId(),
                stock == null ? "?" : stock.getSymbol(),
                stock == null ? "Unknown" : stock.getCompanyName(),
                alert.getType().name(),
                alert.getThreshold(),
                alert.isActive(),
                alert.isTriggered(),
                alert.getLastTriggeredAt(),
                alert.getCreatedAt(),
                stock == null ? null : stock.getLtp(),
                stock == null ? null : stock.getChangePercent()
        );
    }
}
