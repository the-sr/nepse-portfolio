package com.nepse.portfolio.dto.settings;


import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SettingsDto(
            String theme,
            BigDecimal lossThresholdMinor,
            BigDecimal lossThresholdMajor,
            BigDecimal lossThresholdAggressive,
            BigDecimal baseBuyAmount,
            BigDecimal maxPositionPercent,
            BigDecimal maxCapitalPerStock
    ) {
    }

    public record UpdateSettingsRequest(
            @Pattern(regexp = "light|dark", message = "must be light or dark")
            String theme,
            @DecimalMin("0.5") @DecimalMax("90") BigDecimal lossThresholdMinor,
            @DecimalMin("1") @DecimalMax("95") BigDecimal lossThresholdMajor,
            @DecimalMin("1.5") @DecimalMax("99") BigDecimal lossThresholdAggressive,
            @DecimalMin("100") BigDecimal baseBuyAmount,
            @DecimalMin("1") @DecimalMax("100") BigDecimal maxPositionPercent,
            @DecimalMin("100") BigDecimal maxCapitalPerStock
    ) {
    }
}
