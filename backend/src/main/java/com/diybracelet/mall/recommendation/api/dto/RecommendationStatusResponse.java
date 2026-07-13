package com.diybracelet.mall.recommendation.api.dto;

/** Explicit availability state used by the customer form before submission. */
public record RecommendationStatusResponse(String status, boolean available) {
}
