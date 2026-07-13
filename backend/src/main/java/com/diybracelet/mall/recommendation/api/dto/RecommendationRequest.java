package com.diybracelet.mall.recommendation.api.dto;

import com.diybracelet.mall.diy.domain.FitType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Ephemeral consultation input; raw date and full text are never persisted. */
public record RecommendationRequest(
    @PastOrPresent LocalDate birthDate,
    @Size(max = 16) String constellation,
    @Size(max = 16) String zodiac,
    @Size(max = 16) String elementPreference,
    @Size(max = 80) String wish,
    @Size(max = 80) String scene,
    @Size(max = 80) String colorPreference,
    @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") BigDecimal budget,
    @NotNull @DecimalMin("1.00") @DecimalMax("1000.00") BigDecimal wristCircumferenceMm,
    @NotNull FitType fitType,
    @Size(max = 500) String freeText
) {
    @Override
    public String toString() {
        return "RecommendationRequest[constellation=" + constellation
            + ", zodiac=" + zodiac
            + ", elementPreference=" + elementPreference
            + ", wish=" + wish
            + ", scene=" + scene
            + ", colorPreference=" + colorPreference
            + ", budget=" + budget
            + ", wristCircumferenceMm=" + wristCircumferenceMm
            + ", fitType=" + fitType
            + ", sensitiveFieldsRedacted=true"
            + "]";
    }
}
