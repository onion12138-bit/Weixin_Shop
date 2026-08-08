package com.diybracelet.mall.recommendation.domain;

import java.util.List;

/** Strict provider result containing material choices but no size or price. */
public record ModelRecommendation(List<Plan> plans) {
    public ModelRecommendation {
        plans = plans == null ? null : List.copyOf(plans);
    }

    public record Plan(String schemeName, String summary, List<Item> items) {
        public Plan {
            items = items == null ? null : List.copyOf(items);
        }
    }

    public record Item(Long skuId, String reason) {
    }
}
