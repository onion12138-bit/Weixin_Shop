package com.diybracelet.mall.recommendation.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.diybracelet.mall.diy.application.DiyPricingService;
import com.diybracelet.mall.diy.domain.CordOption;
import com.diybracelet.mall.diy.domain.DiyDesignCommand;
import com.diybracelet.mall.diy.domain.DiyEvaluation;
import com.diybracelet.mall.diy.domain.OptionStatus;
import com.diybracelet.mall.diy.infrastructure.mapper.CordOptionMapper;
import com.diybracelet.mall.recommendation.api.dto.RecommendationRequest;
import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationStatusResponse;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelGateway;
import com.diybracelet.mall.recommendation.domain.ModelRecommendation;
import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.recommendation.domain.RecommendationProfile;
import com.diybracelet.mall.recommendation.infrastructure.AiModelConfigMapper;
import com.diybracelet.mall.recommendation.infrastructure.RecommendationCandidateMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Orchestrates one model call and rejects every non-authoritative result. */
@Service
public class RecommendationService {

    private static final long CONFIG_ID = 1L;
    private static final int MAX_SEQUENCE_SIZE = 200;
    private static final String DISCLAIMER =
        "星座、生肖、五行及寓意内容仅供文化娱乐参考，不构成任何确定效果或承诺。";
    private static final List<String> FORBIDDEN_PROMISE_TERMS = List.of(
        "保证", "确保", "必定", "一定会", "百分百", "绝对会", "肯定会",
        "治愈", "治疗", "疗效", "改善疾病", "改善失眠", "稳赚", "必发财"
    );

    private final AiModelConfigMapper configMapper;
    private final RecommendationCandidateMapper candidateMapper;
    private final AiModelGateway modelGateway;
    private final CordOptionMapper cordOptionMapper;
    private final DiyPricingService pricingService;

    public RecommendationService(
            AiModelConfigMapper configMapper,
            RecommendationCandidateMapper candidateMapper,
            AiModelGateway modelGateway,
            CordOptionMapper cordOptionMapper,
            DiyPricingService pricingService
    ) {
        this.configMapper = configMapper;
        this.candidateMapper = candidateMapper;
        this.modelGateway = modelGateway;
        this.cordOptionMapper = cordOptionMapper;
        this.pricingService = pricingService;
    }

    public RecommendationStatusResponse status() {
        AiModelConfig config = configMapper.selectById(CONFIG_ID);
        if (!hasKey(config)) {
            return new RecommendationStatusResponse("NOT_CONFIGURED", false);
        }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return new RecommendationStatusResponse("DISABLED", false);
        }
        return new RecommendationStatusResponse("AVAILABLE", true);
    }

    public RecommendationResponse recommend(RecommendationRequest request) {
        AiModelConfig config = requireAvailableConfiguration();
        List<RecommendationCandidate> candidates =
            candidateMapper.selectSellableCandidates();
        if (candidates == null || candidates.isEmpty()) {
            throw invalid();
        }
        RecommendationProfile profile = profile(request);
        ModelRecommendation model = modelGateway.recommend(
            config,
            profile,
            List.copyOf(candidates)
        );
        try {
            return validateAndBuild(model, request, candidates);
        } catch (BusinessException error) {
            if (error.errorCode() == ErrorCode.AI_INVALID_RECOMMENDATION) {
                throw error;
            }
            throw invalid();
        } catch (RuntimeException error) {
            throw invalid();
        }
    }

    private RecommendationResponse validateAndBuild(
            ModelRecommendation model,
            RecommendationRequest request,
            List<RecommendationCandidate> offeredCandidates
    ) {
        validateModelShape(model);
        Set<Long> offeredIds = offeredCandidates.stream()
            .map(RecommendationCandidate::skuId)
            .collect(Collectors.toUnmodifiableSet());
        LinkedHashMap<Long, Integer> quantities = quantities(model.sequence());
        if (!offeredIds.containsAll(quantities.keySet())) {
            throw invalid();
        }

        LinkedHashMap<Long, String> reasons = reasons(model, quantities.keySet());
        List<Long> selectedIds = new ArrayList<>(quantities.keySet());
        requireCurrentInventory(selectedIds, quantities);

        CordOption cord = firstEnabledCord();
        DiyEvaluation evaluation;
        try {
            evaluation = pricingService.evaluate(new DiyDesignCommand(
                request.wristCircumferenceMm(),
                request.fitType(),
                cord.getId(),
                model.sequence()
            ));
        } catch (RuntimeException error) {
            throw invalid();
        }
        if (!validEvaluation(evaluation, quantities, request)) {
            throw invalid();
        }

        Map<Long, RecommendationCandidate> finalById =
            requireCurrentInventory(selectedIds, quantities);

        List<RecommendationResponse.Item> items = quantities.entrySet().stream()
            .map(entry -> {
                RecommendationCandidate candidate = finalById.get(entry.getKey());
                return new RecommendationResponse.Item(
                    candidate.skuId(),
                    candidate.name(),
                    candidate.imageUrl(),
                    entry.getValue(),
                    reasons.get(entry.getKey())
                );
            })
            .toList();
        return new RecommendationResponse(
            model.schemeName().trim(),
            model.sequence(),
            quantities,
            items,
            model.summary().trim(),
            new RecommendationResponse.CordOptionView(
                cord.getId(), cord.getCode(), cord.getName(), cord.getColorHex()
            ),
            evaluation.beadAmount(),
            evaluation.craftFee(),
            evaluation.cordFee(),
            evaluation.totalAmount(),
            evaluation.quoteFingerprint(),
            DISCLAIMER
        );
    }

    private void validateModelShape(ModelRecommendation model) {
        if (model == null
                || !text(model.schemeName(), 80)
                || !text(model.summary(), 1000)
                || !safeCulturalText(model.summary())
                || model.sequence() == null
                || model.sequence().isEmpty()
                || model.sequence().size() > MAX_SEQUENCE_SIZE
                || model.reasons() == null
                || model.reasons().isEmpty()) {
            throw invalid();
        }
        if (model.sequence().stream().anyMatch(id -> id == null || id <= 0)) {
            throw invalid();
        }
    }

    private LinkedHashMap<Long, Integer> quantities(List<Long> sequence) {
        LinkedHashMap<Long, Integer> quantities = new LinkedHashMap<>();
        for (Long skuId : sequence) {
            quantities.merge(skuId, 1, Integer::sum);
        }
        if (quantities.isEmpty()
                || quantities.values().stream().anyMatch(quantity -> quantity <= 0)) {
            throw invalid();
        }
        return quantities;
    }

    private LinkedHashMap<Long, String> reasons(
            ModelRecommendation model,
            Set<Long> selectedIds
    ) {
        LinkedHashMap<Long, String> reasons = new LinkedHashMap<>();
        for (ModelRecommendation.Reason reason : model.reasons()) {
            if (reason == null
                    || reason.skuId() == null
                    || !selectedIds.contains(reason.skuId())
                    || !text(reason.reason(), 300)
                    || !safeCulturalText(reason.reason())
                    || reasons.putIfAbsent(
                        reason.skuId(),
                        reason.reason().trim()
                    ) != null) {
                throw invalid();
            }
        }
        if (!reasons.keySet().equals(selectedIds)) {
            throw invalid();
        }
        return reasons;
    }

    private Map<Long, RecommendationCandidate> requireCurrentInventory(
            List<Long> selectedIds,
            Map<Long, Integer> quantities
    ) {
        List<RecommendationCandidate> current =
            candidateMapper.selectCurrentSellableByIds(selectedIds);
        if (current == null || current.size() != selectedIds.size()) {
            throw invalid();
        }
        Map<Long, RecommendationCandidate> currentById = uniqueById(current);
        if (currentById.size() != selectedIds.size()) {
            throw invalid();
        }
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            RecommendationCandidate candidate = currentById.get(entry.getKey());
            if (candidate == null || candidate.availableQty() < entry.getValue()) {
                throw invalid();
            }
        }
        return currentById;
    }

    private Map<Long, RecommendationCandidate> uniqueById(
            List<RecommendationCandidate> candidates
    ) {
        Map<Long, RecommendationCandidate> byId = new LinkedHashMap<>();
        for (RecommendationCandidate candidate : candidates) {
            if (candidate == null
                    || candidate.skuId() == null
                    || byId.putIfAbsent(candidate.skuId(), candidate) != null) {
                throw invalid();
            }
        }
        return byId;
    }

    private CordOption firstEnabledCord() {
        List<CordOption> cords = cordOptionMapper.selectList(
            Wrappers.lambdaQuery(CordOption.class)
                .eq(CordOption::getStatus, OptionStatus.ENABLED)
                .orderByAsc(CordOption::getSortOrder)
                .orderByAsc(CordOption::getId)
                .last("limit 1")
        );
        if (cords == null || cords.isEmpty()) {
            throw invalid();
        }
        CordOption cord = cords.get(0);
        if (cord == null
                || cord.getId() == null
                || cord.getStatus() != OptionStatus.ENABLED) {
            throw invalid();
        }
        return cord;
    }

    private boolean validEvaluation(
            DiyEvaluation evaluation,
            Map<Long, Integer> quantities,
            RecommendationRequest request
    ) {
        return evaluation != null
            && evaluation.quotable()
            && evaluation.skuQuantities() != null
            && evaluation.skuQuantities().equals(quantities)
            && evaluation.beadAmount() != null
            && evaluation.craftFee() != null
            && evaluation.cordFee() != null
            && evaluation.totalAmount() != null
            && evaluation.totalAmount().signum() >= 0
            && evaluation.totalAmount().compareTo(request.budget()) <= 0
            && evaluation.quoteFingerprint() != null
            && !evaluation.quoteFingerprint().isBlank();
    }

    private RecommendationProfile profile(RecommendationRequest request) {
        String constellation = normalized(request.constellation());
        String zodiac = normalized(request.zodiac());
        LocalDate date = request.birthDate();
        if (constellation == null && date != null) {
            constellation = constellation(date);
        }
        if (zodiac == null && date != null) {
            zodiac = zodiac(date.getYear());
        }
        return new RecommendationProfile(
            constellation,
            zodiac,
            normalized(request.elementPreference()),
            normalized(request.wish()),
            normalized(request.scene()),
            normalized(request.colorPreference()),
            request.budget(),
            request.wristCircumferenceMm(),
            request.fitType(),
            normalized(request.freeText())
        );
    }

    private String constellation(LocalDate date) {
        int marker = date.getMonthValue() * 100 + date.getDayOfMonth();
        if (marker >= 1222 || marker <= 119) return "摩羯座";
        if (marker <= 218) return "水瓶座";
        if (marker <= 320) return "双鱼座";
        if (marker <= 419) return "白羊座";
        if (marker <= 520) return "金牛座";
        if (marker <= 621) return "双子座";
        if (marker <= 722) return "巨蟹座";
        if (marker <= 822) return "狮子座";
        if (marker <= 922) return "处女座";
        if (marker <= 1023) return "天秤座";
        if (marker <= 1122) return "天蝎座";
        return "射手座";
    }

    private String zodiac(int year) {
        String[] signs = {
            "鼠", "牛", "虎", "兔", "龙", "蛇",
            "马", "羊", "猴", "鸡", "狗", "猪"
        };
        return signs[Math.floorMod(year - 4, signs.length)];
    }

    private AiModelConfig requireAvailableConfiguration() {
        AiModelConfig config = configMapper.selectById(CONFIG_ID);
        if (!hasKey(config)) {
            throw new BusinessException(
                ErrorCode.AI_NOT_CONFIGURED,
                "AI 推荐暂未开放"
            );
        }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(
                ErrorCode.AI_DISABLED,
                "AI 推荐暂停使用"
            );
        }
        return config;
    }

    private boolean hasKey(AiModelConfig config) {
        return config != null
            && config.getEncryptedApiKey() != null
            && !config.getEncryptedApiKey().isBlank();
    }

    private boolean text(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private boolean safeCulturalText(String value) {
        return value != null && FORBIDDEN_PROMISE_TERMS.stream()
            .noneMatch(value::contains);
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BusinessException invalid() {
        return new BusinessException(
            ErrorCode.AI_INVALID_RECOMMENDATION,
            "AI 推荐未通过校验"
        );
    }
}
