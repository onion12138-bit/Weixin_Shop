package com.diybracelet.mall.recommendation.domain;

import java.util.List;


/** 模型返回的材质组合，不接受模型提供的尺寸或价格。 */
public record ModelRecommendation(
    PreferenceAnalysis preferenceAnalysis,
    List<Plan> plans
) {
    public ModelRecommendation(List<Plan> plans) {
        this(null, plans);
    }

    public ModelRecommendation {
        plans = plans == null ? null : List.copyOf(plans);
    }

    /** Optional, non-enforcing explanation used only by the admin call log. */
    public record PreferenceAnalysis(
        String summary,
        List<Long> matchedCandidateIds,
        List<String> evidence
    ) {
        public PreferenceAnalysis {
            matchedCandidateIds = matchedCandidateIds == null
                ? List.of() : List.copyOf(matchedCandidateIds);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record Plan(String schemeName, String summary, List<Item> items) {
        public Plan {
            items = items == null ? null : List.copyOf(items);
        }
    }

    public record Item(Long skuId, String reason) {
    }
}
