package com.diybracelet.mall.recommendation.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/** 管理员更新 AI 参数的请求，可选择替换密钥。 */
public record SaveAiConfigurationRequest(
    @NotBlank @Size(max = 255) String baseUrl,
    @NotBlank @Size(max = 120) String modelName,
    String apiKey,
    boolean enabled,
    @Min(1) @Max(120) int timeoutSeconds,
    @Min(256) @Max(16384) int maxOutputTokens,
    @NotBlank @Size(max = 4000) String recommendationPrompt,
    @NotBlank @Size(max = 4000) String preferencePrompt,
    boolean softPreferenceEnabled,
    @Min(1) @Max(5) int planCount,
    @Min(0) long version
) {

    /** Prevents accidental plaintext key disclosure by structured argument logging. */
    @Override
    public String toString() {
        return "SaveAiConfigurationRequest[apiKeyProvided="
            + (apiKey != null && !apiKey.isBlank())
            + ", enabled=" + enabled
            + ", timeoutSeconds=" + timeoutSeconds
            + ", maxOutputTokens=" + maxOutputTokens
            + ", recommendationPromptProvided="
            + (recommendationPrompt != null && !recommendationPrompt.isBlank())
            + ", preferencePromptProvided="
            + (preferencePrompt != null && !preferencePrompt.isBlank())
            + ", softPreferenceEnabled=" + softPreferenceEnabled
            + ", planCount=" + planCount
            + ", version=" + version
            + "]";
    }
}
