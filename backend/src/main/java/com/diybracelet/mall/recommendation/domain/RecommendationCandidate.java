package com.diybracelet.mall.recommendation.domain;

import java.math.BigDecimal;


/** 模型输入及生成结果复核使用的受控商品信息。 */
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
