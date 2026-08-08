package com.diybracelet.mall.recommendation.api.dto;

import java.util.List;

/** Multiple validated material combinations; sizing and pricing remain in DIY. */
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
