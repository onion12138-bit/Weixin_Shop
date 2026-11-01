package com.diybracelet.mall.recommendation.domain;

/** 只向模型回传白名单纠错码，避免再次拼接旧输出和顾客信息。 */
public record RecommendationRepairFeedback(String code) {
}
