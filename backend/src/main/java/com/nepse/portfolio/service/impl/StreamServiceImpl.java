package com.nepse.portfolio.service.impl;

import com.nepse.portfolio.service.StreamService;

import com.nepse.portfolio.dto.alert.AlertDtos.AlertEventDto;
import com.nepse.portfolio.dto.market.MarketDtos.StockDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class StreamServiceImpl implements StreamService {

    private static final Logger log = LoggerFactory.getLogger(StreamServiceImpl.class);

    private final List<SseEmitter> priceEmitters = new CopyOnWriteArrayList<>();
    private final Map<Long, List<SseEmitter>> alertEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribePrices() {
        SseEmitter emitter = new SseEmitter(0L);
        priceEmitters.add(emitter);
        emitter.onCompletion(() -> priceEmitters.remove(emitter));
        emitter.onTimeout(() -> priceEmitters.remove(emitter));
        emitter.onError(error -> priceEmitters.remove(emitter));
        return emitter;
    }

    public SseEmitter subscribeAlerts(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        alertEmitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeAlert(userId, emitter));
        emitter.onTimeout(() -> removeAlert(userId, emitter));
        emitter.onError(error -> removeAlert(userId, emitter));
        return emitter;
    }

    public void broadcastPrices(List<StockDto> prices) {
        if (prices.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : priceEmitters) {
            send(emitter, "prices", prices);
        }
    }

    public void sendAlert(Long userId, AlertEventDto event) {
        List<SseEmitter> emitters = alertEmitters.get(userId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(emitter, "alert", event);
        }
    }

    @Scheduled(fixedRate = 20000)
    public void heartbeat() {
        for (SseEmitter emitter : priceEmitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception ex) {
                priceEmitters.remove(emitter);
            }
        }
        alertEmitters.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception ex) {
                    removeAlert(userId, emitter);
                }
            }
        });
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException ex) {
            log.debug("Dropping closed SSE emitter: {}", ex.getMessage());
            priceEmitters.remove(emitter);
            alertEmitters.values().forEach(emitters -> emitters.remove(emitter));
        }
    }

    private void removeAlert(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = alertEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                alertEmitters.remove(userId);
            }
        }
    }
}
