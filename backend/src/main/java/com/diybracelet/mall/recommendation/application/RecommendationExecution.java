package com.diybracelet.mall.recommendation.application;

import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;

/** Successful recommendation and the same trace identifier persisted in the admin log. */
public record RecommendationExecution(
    String traceId,
    RecommendationResponse response
) { }
