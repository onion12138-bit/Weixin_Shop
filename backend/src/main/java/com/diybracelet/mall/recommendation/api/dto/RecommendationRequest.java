package com.diybracelet.mall.recommendation.api.dto;

import jakarta.validation.constraints.Size;

/** Birthday is parsed explicitly so the API can return precise birthday errors. */
public record RecommendationRequest(
    String birthDate,
    @Size(max = 8) String wishInspiration,
    @Size(max = 80) String wish
) {
    @Override
    public String toString() {
        return "RecommendationRequest[sensitiveFieldsRedacted=true]";
    }
}
