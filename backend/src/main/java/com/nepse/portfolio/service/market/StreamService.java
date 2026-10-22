package com.nepse.portfolio.service.market;

import com.nepse.portfolio.dto.alert.AlertDtos.AlertEventDto;
import com.nepse.portfolio.dto.market.MarketDtos.StockDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface StreamService {
    SseEmitter subscribePrices();
    SseEmitter subscribeAlerts(Long userId);
    void broadcastPrices(List<StockDto> prices);
    void sendAlert(Long userId, AlertEventDto event);
    void heartbeat();
}
