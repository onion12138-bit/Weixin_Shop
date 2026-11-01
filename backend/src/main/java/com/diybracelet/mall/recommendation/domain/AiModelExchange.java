package com.diybracelet.mall.recommendation.domain;

/** 记录已排除密钥的模型请求和响应，供管理员诊断。 */
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
