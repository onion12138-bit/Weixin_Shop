package com.diybracelet.mall.recommendation.application;

import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.recommendation.api.dto.AiRecommendationLogView;
import com.diybracelet.mall.recommendation.api.dto.RecommendationRequest;
import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelExchange;
import com.diybracelet.mall.recommendation.domain.AiRecommendationLog;
import com.diybracelet.mall.recommendation.domain.ModelRecommendation;
import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.recommendation.infrastructure.AiRecommendationLogMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists complete, admin-only recommendation diagnostics without provider secrets. */
@Service
public class AiRecommendationLogService {

    private static final Set<String> STATUSES = Set.of(
        "PROCESSING", "SUCCEEDED", "FAILED"
    );

    private final AiRecommendationLogMapper mapper;
    private final ObjectMapper objectMapper;

    public AiRecommendationLogService(
            AiRecommendationLogMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public long start(String traceId, RecommendationRequest request) {
        AiRecommendationLog log = new AiRecommendationLog();
        log.setTraceId(traceId);
        log.setStatus("PROCESSING");
        log.setWishSummary(wishSummary(request));
        log.setRequestJson(write(request));
        mapper.insert(log);
        return log.getId();
    }

    @Transactional
    public void captureContext(
            long logId,
            AiModelConfig config,
            List<RecommendationCandidate> candidates
    ) {
        AiRecommendationLog log = base(logId, config, candidates, List.of(), null);
        log.setStatus("PROCESSING");
        mapper.update(log);
    }

    @Transactional
    public void complete(
            long logId,
            AiModelConfig config,
            List<RecommendationCandidate> candidates,
            List<AiModelExchange> exchanges,
            ModelRecommendation model,
            RecommendationResponse response,
            long durationMs
    ) {
        AiRecommendationLog log = base(
            logId, config, candidates, exchanges, durationMs
        );
        log.setStatus("SUCCEEDED");
        applyPreference(log, model);
        log.setFinalResponseJson(write(response));
        mapper.update(log);
    }

    @Transactional
    public void fail(
            long logId,
            AiModelConfig config,
            List<RecommendationCandidate> candidates,
            List<AiModelExchange> exchanges,
            BusinessException error,
            long durationMs
    ) {
        AiRecommendationLog log = base(
            logId, config, candidates, exchanges, durationMs
        );
        log.setStatus("FAILED");
        log.setErrorCode(error.errorCode().name());
        log.setErrorMessage(error.getMessage());
        mapper.update(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<AiRecommendationLogView.Summary> list(
            String status,
            String keyword,
            int page,
            int size
    ) {
        String normalizedStatus = normalize(status);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(
                ErrorCode.COMMON_BAD_REQUEST, "AI 日志状态筛选无效"
            );
        }
        String normalizedKeyword = normalize(keyword);
        long total = mapper.count(normalizedStatus, normalizedKeyword);
        long offset = (long) (page - 1) * size;
        List<AiRecommendationLogView.Summary> items = mapper.selectPage(
            normalizedStatus, normalizedKeyword, offset, size
        ).stream().map(this::summary).toList();
        return new PageResponse<>(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public AiRecommendationLogView.Detail detail(long id) {
        AiRecommendationLog log = require(id);
        return new AiRecommendationLogView.Detail(
            summary(log),
            log.getErrorMessage(),
            log.getRequestJson(),
            log.getConfigSnapshotJson(),
            log.getCandidatesJson(),
            log.getAttemptsJson(),
            log.getPreferenceAnalysisJson(),
            log.getFinalResponseJson(),
            log.getUpdatedAt()
        );
    }

    @Transactional
    public void delete(long id) {
        if (mapper.deleteById(id) != 1) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND, "AI 日志不存在");
        }
    }

    private AiRecommendationLog base(
            long id,
            AiModelConfig config,
            List<RecommendationCandidate> candidates,
            List<AiModelExchange> exchanges,
            Long durationMs
    ) {
        AiRecommendationLog log = new AiRecommendationLog();
        log.setId(id);
        log.setModelName(config == null ? null : config.getModelName());
        log.setCandidateCount(candidates == null ? 0 : candidates.size());
        log.setMatchedCandidateCount(0);
        log.setMatchedPlanCount(0);
        log.setPlanCount(config == null || config.getPlanCount() == null
            ? 0 : config.getPlanCount());
        log.setAttemptCount(exchanges == null ? 0 : exchanges.size());
        log.setDurationMs(durationMs);
        log.setConfigSnapshotJson(config == null ? null : configSnapshot(config));
        log.setCandidatesJson(candidates == null ? null : candidateSnapshot(candidates));
        log.setAttemptsJson(exchanges == null ? null : write(exchanges));
        return log;
    }

    private void applyPreference(
            AiRecommendationLog log,
            ModelRecommendation model
    ) {
        ModelRecommendation.PreferenceAnalysis analysis =
            model == null ? null : model.preferenceAnalysis();
        if (analysis == null) return;
        log.setPreferenceSummary(analysis.summary());
        log.setMatchedCandidateCount(analysis.matchedCandidateIds().size());
        log.setPreferenceAnalysisJson(write(analysis));
        Set<Long> matchedIds = new HashSet<>(analysis.matchedCandidateIds());
        int matchedPlans = (int) model.plans().stream()
            .filter(plan -> plan.items().stream()
                .anyMatch(item -> matchedIds.contains(item.skuId())))
            .count();
        log.setMatchedPlanCount(matchedPlans);
    }

    private String configSnapshot(AiModelConfig config) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("provider", config.getProvider());
        node.put("baseUrl", config.getBaseUrl());
        node.put("modelName", config.getModelName());
        node.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        node.put("timeoutSeconds", config.getTimeoutSeconds());
        node.put("maxOutputTokens", config.getMaxOutputTokens());
        node.put("recommendationPrompt", config.getRecommendationPrompt());
        node.put("preferencePrompt", config.getPreferencePrompt());
        node.put(
            "softPreferenceEnabled",
            Boolean.TRUE.equals(config.getSoftPreferenceEnabled())
        );
        node.put("planCount", config.getPlanCount());
        node.put("version", config.getVersion());
        return write(node);
    }

    private String candidateSnapshot(List<RecommendationCandidate> candidates) {
        ArrayNode array = objectMapper.createArrayNode();
        for (RecommendationCandidate candidate : candidates) {
            ObjectNode node = array.addObject();
            node.put("skuId", candidate.skuId());
            node.put("name", candidate.name());
            node.put("material", candidate.material());
            node.put("color", candidate.color());
            node.put("diameterMm", candidate.diameterMm());
            node.set("tags", tags(candidate.tagsJson()));
        }
        return write(array);
    }

    private JsonNode tags(String tagsJson) {
        try {
            JsonNode node = objectMapper.readTree(tagsJson);
            return node != null && node.isArray()
                ? node : objectMapper.createArrayNode();
        } catch (JsonProcessingException | RuntimeException error) {
            return objectMapper.createArrayNode();
        }
    }

    private AiRecommendationLog require(long id) {
        AiRecommendationLog log = mapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND, "AI 日志不存在");
        }
        return log;
    }

    private AiRecommendationLogView.Summary summary(AiRecommendationLog log) {
        return new AiRecommendationLogView.Summary(
            log.getId(),
            log.getTraceId(),
            log.getStatus(),
            log.getModelName(),
            log.getWishSummary(),
            log.getPreferenceSummary(),
            value(log.getCandidateCount()),
            value(log.getMatchedCandidateCount()),
            value(log.getMatchedPlanCount()),
            value(log.getPlanCount()),
            value(log.getAttemptCount()),
            log.getDurationMs(),
            log.getErrorCode(),
            log.getCreatedAt()
        );
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String wishSummary(RecommendationRequest request) {
        if (request.wish() != null && !request.wish().isBlank()) {
            return truncate(request.wish().trim(), 255);
        }
        if (request.wishInspiration() != null
                && !request.wishInspiration().isBlank()) {
            return truncate(request.wishInspiration().trim(), 255);
        }
        return "未填写期望";
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            return "{\"serializationError\":true}";
        }
    }
}
