package com.nepse.portfolio.alert;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class AlertDtos {

    private AlertDtos() {
    }

    public record AlertDto(
            Long id,
            String symbol,
            String companyName,
            String type,
            BigDecimal threshold,
            boolean active,
            boolean triggered,
            Instant lastTriggeredAt,
            Instant createdAt,
            BigDecimal ltp,
            BigDecimal changePercent
    ) {
    }

    public record CreateAlertRequest(
            @NotBlank String symbol,
            @NotNull AlertType type,
            @NotNull @DecimalMin("0.01") BigDecimal threshold
    ) {
    }

    public record UpdateAlertRequest(
            Boolean active,
            @DecimalMin("0.01") @DecimalMax("1000000") BigDecimal threshold
    ) {
    }

    public record AlertEventDto(
            Long id,
            Long alertId,
            String symbol,
            BigDecimal price,
            String message,
            Instant createdAt
    ) {
    }
}
