package com.diybracelet.mall.recommendation.domain;

import java.util.List;

/** Provider boundary used to inspect models without exposing HTTP details to services. */
public interface AiModelGateway {

    /** Returns the provider model identifiers visible to the configured account. */
    List<String> listModels(AiModelConfig config);

    /** Generates one strict model proposal from a sanitized profile and candidates. */
    ModelRecommendation recommend(
        AiModelConfig config,
        RecommendationProfile profile,
        List<RecommendationCandidate> candidates,
        RecommendationRepairFeedback repairFeedback
    );
}
