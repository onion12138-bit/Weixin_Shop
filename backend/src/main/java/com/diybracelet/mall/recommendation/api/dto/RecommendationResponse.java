package com.diybracelet.mall.recommendation.api.dto;

import java.util.List;


/** 经校验的多套搭配结果，尺寸和价格仍由 DIY 模块计算。 */
public record RecommendationResponse(
    BirthProfileResponse profile,
    List<Plan> plans,
    String culturalDisclaimer
) {
    public RecommendationResponse {
        plans = List.copyOf(plans);
    }

    public record Plan(String schemeName, String summary, List<Item> items) {
        public Plan {
            items = List.copyOf(items);
        }
    }

    public record Item(
        Long skuId,
        String name,
        String imageUrl,
        String reason
    ) {
    }
}
