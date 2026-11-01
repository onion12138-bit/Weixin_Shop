package com.diybracelet.mall.recommendation.api.dto;

import java.time.LocalDateTime;


/** 管理员使用的推荐日志摘要和详情视图，不返回密钥。 */
public final class AiRecommendationLogView {
    private AiRecommendationLogView() { }

    public record Summary(
        long id,
        String traceId,
        String status,
        String modelName,
        String wishSummary,
        String preferenceSummary,
        int candidateCount,
        int matchedCandidateCount,
        int matchedPlanCount,
        int planCount,
        int attemptCount,
        Long durationMs,
        String errorCode,
        LocalDateTime createdAt
    ) { }

    public record Detail(
        Summary summary,
        String errorMessage,
        String requestJson,
        String configSnapshotJson,
        String candidatesJson,
        String attemptsJson,
        String preferenceAnalysisJson,
        String finalResponseJson,
        LocalDateTime updatedAt
    ) { }
}
