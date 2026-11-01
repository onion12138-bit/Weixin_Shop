package com.diybracelet.mall.recommendation.api.dto;

/** 后台 AI 配置视图，排除明文密钥。 */
public record AiConfigurationView(
    String provider,
    String baseUrl,
    String modelName,
    boolean apiKeyConfigured,
    boolean enabled,
    int timeoutSeconds,
    int maxOutputTokens,
    String recommendationPrompt,
    String preferencePrompt,
    boolean softPreferenceEnabled,
    int planCount,
    long version
) {
}
