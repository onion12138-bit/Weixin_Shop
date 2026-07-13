package com.diybracelet.mall.recommendation.domain;

import java.math.BigDecimal;

/** Controlled catalog projection used for model input and post-model revalidation. */
public record RecommendationCandidate(
    Long skuId,
    String name,
    String material,
    String color,
    BigDecimal diameterMm,
    BigDecimal salePrice,
    String tagsJson,
    int availableQty,
    String imageUrl
) {
}
