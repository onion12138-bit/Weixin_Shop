package com.diybracelet.mall.recommendation.application;

import com.diybracelet.mall.recommendation.api.dto.BirthProfileResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationRequest;
import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationStatusResponse;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelGateway;
import com.diybracelet.mall.recommendation.domain.ModelRecommendation;
import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.recommendation.domain.RecommendationProfile;
import com.diybracelet.mall.recommendation.domain.RecommendationRepairFeedback;
import com.diybracelet.mall.recommendation.infrastructure.AiModelConfigMapper;
import com.diybracelet.mall.recommendation.infrastructure.RecommendationCandidateMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Builds birthday-derived, inventory-backed material recommendations without pricing. */
@Service
public class RecommendationService {

    private static final long CONFIG_ID = 1L;
    private static final int MIN_ITEMS_PER_PLAN = 3;
    private static final int MAX_ITEMS_PER_PLAN = 5;
    private static final int MAX_MODEL_ATTEMPTS = 2;
    private static final Set<String> WISH_INSPIRATIONS = Set.of(
        "财运", "姻缘", "事业", "平安", "学业", "相守", "家庭", "活力"
    );
    private static final String DISCLAIMER =
        "星座、生肖、星座元素及寓意内容仅供文化娱乐与搭配参考，不构成任何效果承诺。";
    private static final RecommendationCopySafetyPolicy COPY_SAFETY =
        new RecommendationCopySafetyPolicy();
    private static final Set<ErrorCode> REPAIRABLE_MODEL_ERRORS = Set.of(
        ErrorCode.AI_RESPONSE_FORMAT_INVALID,
        ErrorCode.AI_RESULT_COUNT_INVALID,
        ErrorCode.AI_RESULT_ITEM_COUNT_INVALID,
        ErrorCode.AI_RESULT_DUPLICATE_SKU,
        ErrorCode.AI_RESULT_SKU_INVALID,
        ErrorCode.AI_RESPONSE_CONTENT_UNSAFE
    );

    private final AiModelConfigMapper configMapper;
    private final RecommendationCandidateMapper candidateMapper;
    private final AiModelGateway modelGateway;

    public RecommendationService(
            AiModelConfigMapper configMapper,
            RecommendationCandidateMapper candidateMapper,
            AiModelGateway modelGateway
    ) {
        this.configMapper = configMapper;
        this.candidateMapper = candidateMapper;
        this.modelGateway = modelGateway;
    }

    public RecommendationStatusResponse status() {
        AiModelConfig config = configMapper.selectById(CONFIG_ID);
        if (!hasKey(config) || !hasPrompt(config) || !validPlanCount(config)) {
            return new RecommendationStatusResponse("NOT_CONFIGURED", false);
        }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return new RecommendationStatusResponse("DISABLED", false);
        }
        return new RecommendationStatusResponse("AVAILABLE", true);
    }

    /** Returns the same authoritative birthday derivation used by model requests. */
    public BirthProfileResponse birthProfile(String rawBirthDate) {
        LocalDate birthDate = parseBirthDate(rawBirthDate);
        String constellation = constellation(birthDate);
        return new BirthProfileResponse(
            birthDate.toString(),
            constellation,
            zodiac(birthDate.getYear()),
            constellationElement(constellation)
        );
    }

    public RecommendationResponse recommend(RecommendationRequest request) {
        AiModelConfig config = requireAvailableConfiguration();
        BirthProfileResponse birthProfile = birthProfile(request.birthDate());
        RecommendationProfile profile = new RecommendationProfile(
            birthProfile.birthDate(),
            birthProfile.constellation(),
            birthProfile.zodiac(),
            birthProfile.constellationElement(),
            normalizedWishInspiration(request.wishInspiration()),
            normalized(request.wish())
        );
        List<RecommendationCandidate> candidates = availableCandidates();
        RecommendationRepairFeedback feedback = null;

        for (int attempt = 1; attempt <= MAX_MODEL_ATTEMPTS; attempt++) {
            try {
                ModelRecommendation model = modelGateway.recommend(
                    config, profile, candidates, feedback
                );
                return validateAndBuild(model, birthProfile, config, candidates);
            } catch (BusinessException error) {
                if (attempt >= MAX_MODEL_ATTEMPTS
                        || !REPAIRABLE_MODEL_ERRORS.contains(error.errorCode())) {
                    throw error;
                }
                if (error.errorCode() == ErrorCode.AI_RESULT_SKU_INVALID) {
                    candidates = availableCandidates();
                }
                feedback = new RecommendationRepairFeedback(
                    error.errorCode().name()
                );
            }
        }
        throw responseFormatInvalid();
    }

    private RecommendationResponse validateAndBuild(
            ModelRecommendation model,
            BirthProfileResponse profile,
            AiModelConfig config,
            List<RecommendationCandidate> offeredCandidates
    ) {
        validateModelShape(model, config.getPlanCount());
        Set<Long> offeredIds = offeredCandidates.stream()
            .map(RecommendationCandidate::skuId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        LinkedHashSet<Long> selectedIds = new LinkedHashSet<>();
        for (ModelRecommendation.Plan plan : model.plans()) {
            for (ModelRecommendation.Item item : plan.items()) {
                if (!offeredIds.contains(item.skuId())) {
                    throw skuInvalid();
                }
                selectedIds.add(item.skuId());
            }
        }

        List<RecommendationCandidate> current =
            candidateMapper.selectCurrentSellableByIds(new ArrayList<>(selectedIds));
        Map<Long, RecommendationCandidate> currentById = uniqueById(current);
        if (currentById.size() != selectedIds.size()
                || !currentById.keySet().containsAll(selectedIds)) {
            throw skuInvalid();
        }

        List<RecommendationResponse.Plan> plans = model.plans().stream()
            .map(plan -> new RecommendationResponse.Plan(
                plan.schemeName().trim(),
                plan.summary().trim(),
                plan.items().stream().map(item -> {
                    RecommendationCandidate candidate = currentById.get(item.skuId());
                    return new RecommendationResponse.Item(
                        candidate.skuId(),
                        candidate.name(),
                        candidate.imageUrl(),
                        item.reason().trim()
                    );
                }).toList()
            ))
            .toList();
        return new RecommendationResponse(profile, plans, DISCLAIMER);
    }

    private void validateModelShape(ModelRecommendation model, int expectedPlanCount) {
        if (model == null || model.plans() == null) {
            throw responseFormatInvalid();
        }
        if (model.plans().size() != expectedPlanCount) {
            throw resultCountInvalid();
        }
        for (ModelRecommendation.Plan plan : model.plans()) {
            if (plan == null
                    || !text(plan.schemeName(), 80)
                    || !text(plan.summary(), 1000)
                    || plan.items() == null
                    || plan.items().size() < MIN_ITEMS_PER_PLAN
                    || plan.items().size() > MAX_ITEMS_PER_PLAN) {
                throw responseFormatInvalid();
            }
            if (!COPY_SAFETY.isAllowed(plan.schemeName())
                    || !COPY_SAFETY.isAllowed(plan.summary())) {
                throw contentUnsafe();
            }
            Set<Long> uniqueIds = new LinkedHashSet<>();
            for (ModelRecommendation.Item item : plan.items()) {
                if (item == null
                        || item.skuId() == null
                        || item.skuId() <= 0
                        || !uniqueIds.add(item.skuId())
                        || !text(item.reason(), 300)) {
                    throw responseFormatInvalid();
                }
                if (!COPY_SAFETY.isAllowed(item.reason())) {
                    throw contentUnsafe();
                }
            }
        }
    }

    private Map<Long, RecommendationCandidate> uniqueById(
            List<RecommendationCandidate> candidates
    ) {
        if (candidates == null) return Map.of();
        Map<Long, RecommendationCandidate> byId = new LinkedHashMap<>();
        for (RecommendationCandidate candidate : candidates) {
            if (candidate == null
                    || candidate.skuId() == null
                    || byId.putIfAbsent(candidate.skuId(), candidate) != null) {
                throw new BusinessException(
                    ErrorCode.COMMON_INTERNAL_ERROR,
                    "推荐商品数据异常"
                );
            }
        }
        return byId;
    }

    private List<RecommendationCandidate> availableCandidates() {
        List<RecommendationCandidate> candidates =
            candidateMapper.selectSellableCandidates();
        if (candidates == null || candidates.isEmpty()) {
            throw new BusinessException(
                ErrorCode.CATALOG_EMPTY,
                "当前没有可供推荐的在售库存珠子"
            );
        }
        return List.copyOf(candidates);
    }

    private AiModelConfig requireAvailableConfiguration() {
        AiModelConfig config = configMapper.selectById(CONFIG_ID);
        if (!hasKey(config)) {
            throw new BusinessException(
                ErrorCode.AI_NOT_CONFIGURED,
                "尚未配置 AI API Key"
            );
        }
        if (!hasPrompt(config) || !validPlanCount(config)) {
            throw new BusinessException(
                ErrorCode.AI_PROMPT_NOT_CONFIGURED,
                "尚未配置 AI 推荐提示词或推荐套数"
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

    private LocalDate parseBirthDate(String rawBirthDate) {
        if (rawBirthDate == null || rawBirthDate.isBlank()) {
            throw new BusinessException(
                ErrorCode.BIRTH_DATE_REQUIRED,
                "请选择生日"
            );
        }
        try {
            LocalDate birthDate = LocalDate.parse(rawBirthDate.trim());
            if (birthDate.isAfter(LocalDate.now())) {
                throw birthDateInvalid();
            }
            return birthDate;
        } catch (DateTimeParseException error) {
            throw birthDateInvalid();
        }
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

    private String constellationElement(String constellation) {
        return switch (constellation) {
            case "白羊座", "狮子座", "射手座" -> "火象";
            case "金牛座", "处女座", "摩羯座" -> "土象";
            case "双子座", "天秤座", "水瓶座" -> "风象";
            case "巨蟹座", "天蝎座", "双鱼座" -> "水象";
            default -> throw new IllegalStateException("Unknown constellation");
        };
    }

    private boolean hasKey(AiModelConfig config) {
        return config != null
            && config.getEncryptedApiKey() != null
            && !config.getEncryptedApiKey().isBlank();
    }

    private boolean hasPrompt(AiModelConfig config) {
        return config != null
            && config.getRecommendationPrompt() != null
            && !config.getRecommendationPrompt().isBlank();
    }

    private boolean validPlanCount(AiModelConfig config) {
        return config != null
            && config.getPlanCount() != null
            && config.getPlanCount() >= 1
            && config.getPlanCount() <= 5;
    }

    private boolean text(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizedWishInspiration(String value) {
        String normalized = normalized(value);
        if (normalized == null) return null;
        if (WISH_INSPIRATIONS.contains(normalized)) return normalized;
        throw new BusinessException(
            ErrorCode.COMMON_BAD_REQUEST,
            "心愿灵感无效"
        );
    }

    private BusinessException birthDateInvalid() {
        return new BusinessException(
            ErrorCode.BIRTH_DATE_INVALID,
            "生日格式不正确或晚于当前日期"
        );
    }

    private BusinessException responseFormatInvalid() {
        return new BusinessException(
            ErrorCode.AI_RESPONSE_FORMAT_INVALID,
            "AI 返回格式不正确"
        );
    }

    private BusinessException resultCountInvalid() {
        return new BusinessException(
            ErrorCode.AI_RESULT_COUNT_INVALID,
            "AI 返回的推荐套数与后台配置不一致"
        );
    }

    private BusinessException skuInvalid() {
        return new BusinessException(
            ErrorCode.AI_RESULT_SKU_INVALID,
            "AI 选择了不存在、下架或无库存的商品"
        );
    }

    private BusinessException contentUnsafe() {
        return new BusinessException(
            ErrorCode.AI_RESPONSE_CONTENT_UNSAFE,
            "AI 推荐文案包含不允许的效果承诺"
        );
    }
}
