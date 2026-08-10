package com.diybracelet.mall.recommendation.api.dto;

import java.time.LocalDateTime;

/** Secret-free summary and detail views for administrator recommendation tracing. */
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
