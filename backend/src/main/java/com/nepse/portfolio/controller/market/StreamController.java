package com.nepse.portfolio.controller.market;

import lombok.RequiredArgsConstructor;
import com.nepse.portfolio.service.market.StreamService;

import com.nepse.portfolio.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;
    private final CurrentUser currentUser;


    @GetMapping(value = "/api/market/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter prices() {
        return streamService.subscribePrices();
    }

    @GetMapping(value = "/api/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter alerts() {
        return streamService.subscribeAlerts(currentUser.id());
    }
}
