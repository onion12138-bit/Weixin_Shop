package com.diybracelet.mall.recommendation.domain;

/** 本次模型请求所需的顾客生日派生信息和搭配偏好。 */
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
