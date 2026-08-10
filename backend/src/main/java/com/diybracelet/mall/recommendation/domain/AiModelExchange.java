package com.diybracelet.mall.recommendation.domain;

/** Sanitized model HTTP exchange captured for the admin recommendation log. */
public record AiModelExchange(
    String requestJson,
    Integer responseStatus,
    String responseBody,
    long durationMs,
    String errorCode
) {
    /** Avoids accidental prompt or model-output disclosure in framework logs. */
    @Override
    public String toString() {
        return "AiModelExchange[responseStatus=" + responseStatus
            + ", durationMs=" + durationMs
            + ", errorCode=" + errorCode
            + ", sensitiveFieldsRedacted=true]";
    }
}
