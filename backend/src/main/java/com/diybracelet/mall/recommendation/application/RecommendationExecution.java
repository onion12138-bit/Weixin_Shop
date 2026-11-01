package com.diybracelet.mall.recommendation.application;

import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;


/** 推荐结果和对应的审计日志追踪标识。 */
public record RecommendationExecution(
    String traceId,
    RecommendationResponse response
) { }
