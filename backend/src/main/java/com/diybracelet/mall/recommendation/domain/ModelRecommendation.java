package com.diybracelet.mall.recommendation.domain;

import java.util.List;

/** Strict provider output. Price and quantity fields are deliberately absent. */
public record ModelRecommendation(
    String schemeName,
    List<Long> sequence,
    List<Reason> reasons,
    String summary
) {
    public ModelRecommendation {
        sequence = sequence == null ? null : List.copyOf(sequence);
        reasons = reasons == null ? null : List.copyOf(reasons);
    }

    public record Reason(Long skuId, String reason) {
    }
}
