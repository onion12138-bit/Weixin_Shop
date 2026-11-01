package com.diybracelet.mall.recommendation.api.dto;

/** 小程序提交推荐前使用的 AI 可用状态。 */
public record RecommendationStatusResponse(String status, boolean available) {
}
