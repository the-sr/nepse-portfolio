package com.nepse.portfolio.utils.ml;


import lombok.extern.slf4j.Slf4j;
import com.nepse.portfolio.dto.analysis.Indicators;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class MlClient {


    private final RestClient restClient;
    private final boolean enabled;

    public MlClient(@Value("${app.ml.base-url:http://localhost:8000}") String baseUrl,
                    @Value("${app.ml.timeout-ms:2500}") int timeoutMs,
                    @Value("${app.ml.enabled:true}") boolean enabled) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.enabled = enabled;
    }

    public record PredictRequest(String symbol, BigDecimal lossPercent, int holdingDays,
                                 BigDecimal concentration) {
    }

    public record Prediction(String symbol, String action, double confidence,
                             Map<String, Double> probabilities, String modelVersion,
                             List<String> reasons) {
    }

    public Optional<Prediction> predict(String symbol, BigDecimal lossPercent, int holdingDays,
                                        BigDecimal concentration) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            Prediction prediction = restClient.post()
                    .uri("/predict")
                    .body(new PredictRequest(symbol, lossPercent, holdingDays, concentration))
                    .retrieve()
                    .body(Prediction.class);
            if (prediction == null || prediction.action() == null) {
                return Optional.empty();
            }
            return Optional.of(prediction);
        } catch (Exception ex) {
            log.debug("ML service unavailable for {}: {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> status() {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get().uri("/model/status").retrieve().body(Map.class);
            return Optional.ofNullable(body);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
