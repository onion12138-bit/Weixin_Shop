package com.diybracelet.mall.recommendation.api.dto;

/** Secret-free administration view of the singleton AI configuration. */
public record AiConfigurationView(
    String provider,
    String baseUrl,
    String modelName,
    boolean apiKeyConfigured,
    boolean enabled,
    int timeoutSeconds,
    int maxOutputTokens,
    String recommendationPrompt,
    int planCount,
    long version
) {
}
