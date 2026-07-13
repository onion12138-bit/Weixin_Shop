package com.diybracelet.mall.recommendation.domain;

import com.diybracelet.mall.diy.domain.FitType;
import java.math.BigDecimal;

/** Sanitized provider profile. It intentionally has no raw birth-date field. */
public record RecommendationProfile(
    String constellation,
    String zodiac,
    String elementPreference,
    String wish,
    String scene,
    String colorPreference,
    BigDecimal budget,
    BigDecimal wristCircumferenceMm,
    FitType fitType,
    String freeText
) {
    @Override
    public String toString() {
        return "RecommendationProfile[constellation=" + constellation
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
