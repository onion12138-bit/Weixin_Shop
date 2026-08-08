package com.diybracelet.mall.recommendation.domain;

/** Derived customer information sent only to the configured model for this request. */
public record RecommendationProfile(
    String birthDate,
    String constellation,
    String zodiac,
    String constellationElement,
    String wishInspiration,
    String wish
) {
    @Override
    public String toString() {
        return "RecommendationProfile[constellation=" + constellation
            + ", zodiac=" + zodiac
            + ", constellationElement=" + constellationElement
            + ", sensitiveFieldsRedacted=true]";
    }
}
