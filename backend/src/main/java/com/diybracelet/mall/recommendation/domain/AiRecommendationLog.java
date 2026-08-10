package com.diybracelet.mall.recommendation.domain;

import java.time.LocalDateTime;

/** Persisted, secret-free trace of one recommendation request. */
public class AiRecommendationLog {
    private Long id;
    private String traceId;
    private String status;
    private String modelName;
    private String wishSummary;
    private String preferenceSummary;
    private Integer candidateCount;
    private Integer matchedCandidateCount;
    private Integer matchedPlanCount;
    private Integer planCount;
    private Integer attemptCount;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private String requestJson;
    private String configSnapshotJson;
    private String candidatesJson;
    private String attemptsJson;
    private String preferenceAnalysisJson;
    private String finalResponseJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getWishSummary() { return wishSummary; }
    public void setWishSummary(String wishSummary) { this.wishSummary = wishSummary; }
    public String getPreferenceSummary() { return preferenceSummary; }
    public void setPreferenceSummary(String preferenceSummary) { this.preferenceSummary = preferenceSummary; }
    public Integer getCandidateCount() { return candidateCount; }
    public void setCandidateCount(Integer candidateCount) { this.candidateCount = candidateCount; }
    public Integer getMatchedCandidateCount() { return matchedCandidateCount; }
    public void setMatchedCandidateCount(Integer matchedCandidateCount) { this.matchedCandidateCount = matchedCandidateCount; }
    public Integer getMatchedPlanCount() { return matchedPlanCount; }
    public void setMatchedPlanCount(Integer matchedPlanCount) { this.matchedPlanCount = matchedPlanCount; }
    public Integer getPlanCount() { return planCount; }
    public void setPlanCount(Integer planCount) { this.planCount = planCount; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String requestJson) { this.requestJson = requestJson; }
    public String getConfigSnapshotJson() { return configSnapshotJson; }
    public void setConfigSnapshotJson(String configSnapshotJson) { this.configSnapshotJson = configSnapshotJson; }
    public String getCandidatesJson() { return candidatesJson; }
    public void setCandidatesJson(String candidatesJson) { this.candidatesJson = candidatesJson; }
    public String getAttemptsJson() { return attemptsJson; }
    public void setAttemptsJson(String attemptsJson) { this.attemptsJson = attemptsJson; }
    public String getPreferenceAnalysisJson() { return preferenceAnalysisJson; }
    public void setPreferenceAnalysisJson(String preferenceAnalysisJson) { this.preferenceAnalysisJson = preferenceAnalysisJson; }
    public String getFinalResponseJson() { return finalResponseJson; }
    public void setFinalResponseJson(String finalResponseJson) { this.finalResponseJson = finalResponseJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
