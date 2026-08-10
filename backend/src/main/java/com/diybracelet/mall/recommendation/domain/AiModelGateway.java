package com.diybracelet.mall.recommendation.domain;

import java.util.List;
import java.util.function.Consumer;

/** Provider boundary used to inspect models without exposing HTTP details to services. */
public interface AiModelGateway {

    /** Returns the provider model identifiers visible to the configured account. */
    List<String> listModels(AiModelConfig config);

    /** Generates one strict model proposal from a sanitized profile and candidates. */
    default ModelRecommendation recommend(
        AiModelConfig config,
        RecommendationProfile profile,
        List<RecommendationCandidate> candidates,
        RecommendationRepairFeedback repairFeedback
    ) {
        return recommend(config, profile, candidates, repairFeedback, exchange -> { });
    }

    /** Generates a proposal and reports sanitized provider exchanges for auditing. */
    ModelRecommendation recommend(
        AiModelConfig config,
        RecommendationProfile profile,
        List<RecommendationCandidate> candidates,
        RecommendationRepairFeedback repairFeedback,
        Consumer<AiModelExchange> exchangeObserver
    );
}
