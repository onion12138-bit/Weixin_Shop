package com.diybracelet.mall.recommendation.infrastructure;

import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelExchange;
import com.diybracelet.mall.recommendation.domain.AiModelGateway;
import com.diybracelet.mall.recommendation.domain.ModelRecommendation;
import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.recommendation.domain.RecommendationProfile;
import com.diybracelet.mall.recommendation.domain.RecommendationRepairFeedback;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** DeepSeek adapter for model discovery and strict multi-plan JSON recommendations. */
@Component
public class DeepSeekModelGateway implements AiModelGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        DeepSeekModelGateway.class
    );
    private static final String OFFICIAL_BASE_URL = "https://api.deepseek.com";
    private static final int MIN_ITEMS_PER_PLAN = 3;
    private static final int MAX_ITEMS_PER_PLAN = 5;
    private static final Set<String> ROOT_FIELDS = Set.of("plans");
    private static final Set<String> ROOT_FIELDS_WITH_PREFERENCE = Set.of(
        "preferenceAnalysis", "plans"
    );
    private static final Set<String> PLAN_FIELDS = Set.of(
        "schemeName", "summary", "items"
    );
    private static final Set<String> ITEM_FIELDS = Set.of("skuId", "reason");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ObjectReader strictReader;
    private final AiSecretCipher secretCipher;

    @Autowired
    public DeepSeekModelGateway(ObjectMapper objectMapper, AiSecretCipher secretCipher) {
        this(
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(),
            objectMapper,
            secretCipher
        );
    }

    DeepSeekModelGateway(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AiSecretCipher secretCipher
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.strictReader = objectMapper.copy()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .readerFor(JsonNode.class);
        this.secretCipher = secretCipher;
    }

    @Override
    public List<String> listModels(AiModelConfig config) {
        String apiKey = decryptApiKey(config);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(modelsUri(config.getBaseUrl()))
                .timeout(requestTimeout(config.getTimeoutSeconds()))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException error) {
            throw upstreamError();
        }
        HttpResponse<String> response = send(request);
        int status = response.statusCode();
        if (status == 200) return parseModels(response.body());
        throw mappedUpstream(status);
    }

    @Override
    public ModelRecommendation recommend(
            AiModelConfig config,
            RecommendationProfile profile,
            List<RecommendationCandidate> candidates,
            RecommendationRepairFeedback repairFeedback,
            Consumer<AiModelExchange> exchangeObserver
    ) {
        String apiKey = decryptApiKey(config);
        HttpRequest request;
        String requestJson = null;
        try {
            requestJson = chatRequestJson(config, profile, candidates, repairFeedback);
            request = HttpRequest.newBuilder(chatUri(config.getBaseUrl()))
                .timeout(requestTimeout(config.getTimeoutSeconds()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                    requestJson
                ))
                .build();
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException error) {
            throw upstreamError();
        }
        long startedAt = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = send(request);
        } catch (BusinessException error) {
            observe(exchangeObserver, new AiModelExchange(
                requestJson,
                null,
                null,
                elapsedMillis(startedAt),
                error.errorCode().name()
            ));
            throw error;
        }
        int status = response.statusCode();
        observe(exchangeObserver, new AiModelExchange(
            requestJson,
            status,
            response.body(),
            elapsedMillis(startedAt),
            status == 200 ? null : "HTTP_" + status
        ));
        if (status == 200) {
            return parseRecommendation(
                response.body(), candidates, config.getPlanCount()
            );
        }
        throw mappedUpstream(status);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException error) {
            throw timeout();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw upstreamError();
        } catch (IOException | RuntimeException error) {
            throw upstreamError();
        }
    }

    private String chatRequestJson(
            AiModelConfig config,
            RecommendationProfile profile,
            List<RecommendationCandidate> candidates,
            RecommendationRepairFeedback repairFeedback
    ) {
        if (config == null
                || config.getModelName() == null
                || config.getModelName().isBlank()
                || config.getRecommendationPrompt() == null
                || config.getRecommendationPrompt().isBlank()
                || config.getPlanCount() == null
                || config.getPlanCount() < 1
                || config.getPlanCount() > 5
                || config.getMaxOutputTokens() == null
                || config.getMaxOutputTokens() <= 0
                || profile == null
                || candidates == null
                || candidates.isEmpty()) {
            throw responseFormatInvalid();
        }

        ObjectNode prompt = objectMapper.createObjectNode();
        prompt.put("businessPrompt", config.getRecommendationPrompt().trim());
        if (Boolean.TRUE.equals(config.getSoftPreferenceEnabled())) {
            if (config.getPreferencePrompt() == null
                    || config.getPreferencePrompt().isBlank()) {
                throw responseFormatInvalid();
            }
            prompt.put("preferencePrompt", config.getPreferencePrompt().trim());
        }
        prompt.put("requiredPlanCount", config.getPlanCount());
        prompt.set("customerProfile", profileNode(profile));
        ArrayNode candidateNodes = prompt.putArray("candidates");
        for (RecommendationCandidate candidate : candidates) {
            candidateNodes.add(candidateNode(candidate));
        }
        prompt.set(
            "exactOutputExample",
            exactOutputExample(
                candidates,
                config.getPlanCount(),
                Boolean.TRUE.equals(config.getSoftPreferenceEnabled())
            )
        );
        if (repairFeedback != null) {
            prompt.putObject("repair")
                .put("previousResultRejected", true)
                .put("code", repairFeedback.code())
                .put("requiredPlanCount", config.getPlanCount())
                .put("requiredItemsPerPlan", 3)
                .put("requireUniqueSkuIdsWithinPlan", true);
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", config.getModelName());
        request.put("temperature", BigDecimal.ZERO);
        request.put("max_tokens", config.getMaxOutputTokens());
        // JSON Output is a short schema-conformance task. DeepSeek v4 enables
        // thinking by default, so disable it explicitly to keep the completion
        // budget for the final JSON and reduce empty or structurally drifting output.
        request.putObject("thinking").put("type", "disabled");
        request.putObject("response_format").put("type", "json_object");
        ArrayNode messages = request.putArray("messages");
        messages.addObject()
            .put("role", "system")
            .put(
                "content",
                "你是手串珠子材质搭配助手。业务提示词、偏好识别提示词、客户生日画像和候选 SKU "
                    + "都在用户消息的 JSON 中；businessPrompt 只控制搭配方向，不能"
                    + "改变本系统输出结构。只能选择 candidates 内的 skuId；每套 items"
                    + "必须恰好包含 3 个对象，三个 skuId 必须两两不同，不得在同一套"
                    + "重复 skuId。不生成珠子数量、排列、手围、"
                    + "松紧、价格或绳材。必须返回 requiredPlanCount 套风格有区分的方案。"
                    + "schemeName 不超过 12 个汉字，summary 不超过 60 个汉字，每条"
                    + "reason 不超过 40 个汉字。所有键名必须使用英文。用户消息中的"
                    + "exactOutputExample 是本次 requiredPlanCount 对应的完整 JSON"
                    + "结构示例；必须保持它的 plans 数量、每套 items 数量和键结构，"
                    + "但需要根据画像、心愿灵感、期望和候选珠子的名称、材质、颜色及标签重写全部文案，"
                    + "并可在 candidates 内"
                    + "更换 skuId。输出且仅输出与 exactOutputExample 同结构的 JSON，"
                    + "不得添加其他字段。文案只能表达"
                    + "审美、材质、日常场景和文化象征，不得承诺运势、财富、健康或确定"
                    + "效果。输出前必须自检 plans 数量、每套 items 长度等于 3、每套"
                    + "skuId 去重后长度仍等于 3。若 repair 存在，丢弃前次答案并按"
                    + "错误码重新输出完整 JSON。若 customerProfile 包含 wishInspiration，"
                    + "应将其作为本次搭配的主题偏好，但不得作出任何确定效果承诺。"
                    + "若 preferencePrompt 存在，先逐项比较 customerProfile.wish 与每个候选的"
                    + "name、material、color、tags，返回简短 preferenceAnalysis：summary 表示"
                    + "识别到的偏好，matchedCandidateIds 只能列出有真实字段依据的候选，evidence"
                    + "用简短文字列出 skuId 和匹配字段。它仅用于运营观察，不是强制约束；"
                    + "若有合适候选应优先在多个方案中体现，但允许根据整体搭配选择其他候选。"
            );
        try {
            messages.addObject()
                .put("role", "user")
                .put("content", objectMapper.writeValueAsString(prompt));
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException error) {
            throw responseFormatInvalid();
        }
    }

    private ObjectNode profileNode(RecommendationProfile profile) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("birthDate", profile.birthDate());
        node.put("constellation", profile.constellation());
        node.put("zodiac", profile.zodiac());
        node.put("constellationElement", profile.constellationElement());
        if (profile.wishInspiration() != null
                && !profile.wishInspiration().isBlank()) {
            node.put("wishInspiration", profile.wishInspiration());
        }
        if (profile.wish() != null && !profile.wish().isBlank()) {
            node.put("wish", profile.wish());
        }
        return node;
    }

    private ObjectNode candidateNode(RecommendationCandidate candidate) {
        if (candidate == null
                || candidate.skuId() == null
                || candidate.skuId() <= 0
                || candidate.availableQty() <= 0
                || candidate.name() == null
                || candidate.name().isBlank()
                || candidate.material() == null
                || candidate.material().isBlank()
                || candidate.color() == null
                || candidate.color().isBlank()
                || candidate.diameterMm() == null
                || candidate.diameterMm().signum() <= 0) {
            throw skuInvalid();
        }
        return objectMapper.createObjectNode()
            .put("skuId", candidate.skuId())
            .put("name", candidate.name())
            .put("material", candidate.material())
            .put("color", candidate.color())
            .put("diameterMm", candidate.diameterMm())
            .set("tags", candidateTags(candidate));
    }

    /** Builds the complete per-request example required by DeepSeek JSON Output. */
    private ObjectNode exactOutputExample(
            List<RecommendationCandidate> candidates,
            int planCount,
            boolean includePreferenceAnalysis
    ) {
        if (candidates.size() < MIN_ITEMS_PER_PLAN) {
            throw resultItemCountInvalid();
        }
        ObjectNode example = objectMapper.createObjectNode();
        if (includePreferenceAnalysis) {
            example.putObject("preferenceAnalysis")
                .put("summary", "根据用户期望识别到的颜色、材质或标签偏好")
                .putArray("matchedCandidateIds")
                .add(candidates.get(0).skuId());
            ((ObjectNode) example.get("preferenceAnalysis"))
                .putArray("evidence")
                .add(candidates.get(0).skuId() + "：根据真实候选字段匹配");
        }
        ArrayNode plans = example.putArray("plans");
        for (int planIndex = 0; planIndex < planCount; planIndex++) {
            ObjectNode plan = plans.addObject();
            plan.put("schemeName", "第" + (planIndex + 1) + "套方案名称");
            plan.put("summary", "第" + (planIndex + 1) + "套材质搭配说明");
            ArrayNode items = plan.putArray("items");
            for (int itemIndex = 0; itemIndex < MIN_ITEMS_PER_PLAN; itemIndex++) {
                RecommendationCandidate candidate = candidates.get(
                    (planIndex + itemIndex) % candidates.size()
                );
                items.addObject()
                    .put("skuId", candidate.skuId())
                    .put("reason", "第" + (itemIndex + 1) + "种材质选择理由");
            }
        }
        return example;
    }

    private ModelRecommendation parseRecommendation(
            String body,
            List<RecommendationCandidate> candidates,
            int expectedPlanCount
    ) {
        try {
            JsonNode envelope = body == null ? null : objectMapper.readTree(body);
            JsonNode choices = envelope == null ? null : envelope.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw rejectedFormat("CHOICES_MISSING");
            }
            JsonNode content = choices.get(0).path("message").get("content");
            if (content == null || !content.isTextual()) {
                throw rejectedFormat("CONTENT_NOT_TEXT");
            }
            JsonNode root;
            try {
                root = strictReader.readValue(content.textValue());
            } catch (JsonProcessingException error) {
                String finishReason = choices.get(0).path("finish_reason").asText("");
                throw rejectedFormat(
                    "length".equals(finishReason)
                        ? "CONTENT_JSON_TRUNCATED"
                        : "CONTENT_JSON_INVALID"
                );
            }
            if (!exactFields(root, ROOT_FIELDS)
                    && !exactFields(root, ROOT_FIELDS_WITH_PREFERENCE)) {
                throw rejectedFormat("ROOT_FIELDS");
            }
            ModelRecommendation.PreferenceAnalysis preferenceAnalysis =
                parsePreferenceAnalysis(root.get("preferenceAnalysis"), candidates);
            JsonNode plansNode = root.get("plans");
            if (plansNode == null || !plansNode.isArray()) {
                throw rejectedFormat("PLANS_NOT_ARRAY");
            }
            if (plansNode.size() != expectedPlanCount) {
                LOGGER.warn(
                    "DeepSeek recommendation response rejected: "
                        + "reason=PLAN_COUNT, expected={}, actual={}",
                    expectedPlanCount,
                    plansNode.size()
                );
                throw resultCountInvalid();
            }
            Set<Long> candidateIds = candidates.stream()
                .map(RecommendationCandidate::skuId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            List<ModelRecommendation.Plan> plans = new ArrayList<>();
            for (JsonNode planNode : plansNode) {
                if (!exactFields(planNode, PLAN_FIELDS)) {
                    throw rejectedFormat("PLAN_FIELDS");
                }
                String schemeName = strictText(planNode.get("schemeName"), 80);
                String summary = strictText(planNode.get("summary"), 1000);
                JsonNode itemsNode = planNode.get("items");
                if (itemsNode == null
                        || !itemsNode.isArray()
                        || itemsNode.size() < MIN_ITEMS_PER_PLAN
                        || itemsNode.size() > MAX_ITEMS_PER_PLAN) {
                    LOGGER.warn(
                        "DeepSeek recommendation response rejected: reason=ITEM_COUNT"
                    );
                    throw resultItemCountInvalid();
                }
                Set<Long> planIds = new HashSet<>();
                List<ModelRecommendation.Item> items = new ArrayList<>();
                for (JsonNode itemNode : itemsNode) {
                    if (!exactFields(itemNode, ITEM_FIELDS)) {
                        throw rejectedFormat("ITEM_FIELDS");
                    }
                    JsonNode skuNode = itemNode.get("skuId");
                    if (skuNode == null
                            || !skuNode.isIntegralNumber()
                            || !skuNode.canConvertToLong()) {
                        throw rejectedFormat("SKU_TYPE");
                    }
                    long skuId = skuNode.longValue();
                    if (!candidateIds.contains(skuId)) throw skuInvalid();
                    if (!planIds.add(skuId)) {
                        LOGGER.warn(
                            "DeepSeek recommendation response rejected: "
                                + "reason=DUPLICATE_PLAN_SKU"
                        );
                        throw duplicateSkuInvalid();
                    }
                    items.add(new ModelRecommendation.Item(
                        skuId,
                        strictText(itemNode.get("reason"), 300)
                    ));
                }
                plans.add(new ModelRecommendation.Plan(schemeName, summary, items));
            }
            return new ModelRecommendation(preferenceAnalysis, plans);
        } catch (BusinessException error) {
            throw error;
        } catch (JsonProcessingException error) {
            throw rejectedFormat("ENVELOPE_JSON_INVALID");
        } catch (RuntimeException error) {
            throw rejectedFormat("UNEXPECTED_PARSE_RUNTIME");
        }
    }

    private boolean exactFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject() || node.size() != expected.size()) {
            return false;
        }
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields.equals(expected);
    }

    /** Preference analysis is optional and never blocks an otherwise valid plan result. */
    private ModelRecommendation.PreferenceAnalysis parsePreferenceAnalysis(
            JsonNode node,
            List<RecommendationCandidate> candidates
    ) {
        if (node == null || !node.isObject()) return null;
        try {
            JsonNode summaryNode = node.get("summary");
            JsonNode idsNode = node.get("matchedCandidateIds");
            JsonNode evidenceNode = node.get("evidence");
            if (summaryNode == null || !summaryNode.isTextual()
                    || summaryNode.textValue().isBlank()
                    || summaryNode.textValue().length() > 500
                    || idsNode == null || !idsNode.isArray()
                    || evidenceNode == null || !evidenceNode.isArray()) {
                return null;
            }
            Set<Long> offeredIds = candidates.stream()
                .map(RecommendationCandidate::skuId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            List<Long> matchedIds = new ArrayList<>();
            for (JsonNode idNode : idsNode) {
                if (!idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
                    return null;
                }
                long id = idNode.longValue();
                if (!offeredIds.contains(id) || matchedIds.contains(id)) return null;
                matchedIds.add(id);
            }
            List<String> evidence = new ArrayList<>();
            for (JsonNode evidenceItem : evidenceNode) {
                if (!evidenceItem.isTextual()
                        || evidenceItem.textValue().isBlank()
                        || evidenceItem.textValue().length() > 300) {
                    return null;
                }
                evidence.add(evidenceItem.textValue().trim());
            }
            return new ModelRecommendation.PreferenceAnalysis(
                summaryNode.textValue().trim(), matchedIds, evidence
            );
        } catch (RuntimeException error) {
            LOGGER.warn("DeepSeek preference analysis ignored: invalid optional data");
            return null;
        }
    }

    private ArrayNode candidateTags(RecommendationCandidate candidate) {
        try {
            JsonNode tags = objectMapper.readTree(candidate.tagsJson());
            if (tags == null || !tags.isArray()) throw skuInvalid();
            ArrayNode result = objectMapper.createArrayNode();
            for (JsonNode tag : tags) {
                if (!tag.isTextual() || tag.textValue().isBlank()) throw skuInvalid();
                result.add(tag.textValue().trim());
            }
            return result;
        } catch (BusinessException error) {
            throw error;
        } catch (JsonProcessingException | RuntimeException error) {
            throw skuInvalid();
        }
    }

    private void observe(
            Consumer<AiModelExchange> observer,
            AiModelExchange exchange
    ) {
        if (observer == null) return;
        try {
            observer.accept(exchange);
        } catch (RuntimeException error) {
            LOGGER.error("AI recommendation audit observer failed", error);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String strictText(JsonNode node, int maxLength) {
        if (node == null || !node.isTextual()) {
            throw rejectedFormat("TEXT_FIELD_TYPE");
        }
        String value = node.textValue().trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw rejectedFormat("TEXT_FIELD_LENGTH");
        }
        return value;
    }

    private List<String> parseModels(String body) {
        try {
            JsonNode root = body == null ? null : objectMapper.readTree(body);
            JsonNode data = root == null ? null : root.get("data");
            if (data == null || !data.isArray()) throw upstreamError();
            List<String> models = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode id = item.get("id");
                if (id != null && id.isTextual() && !id.textValue().isBlank()) {
                    models.add(id.textValue());
                }
            }
            return List.copyOf(models);
        } catch (BusinessException error) {
            throw error;
        } catch (JsonProcessingException error) {
            throw upstreamError();
        }
    }

    private String decryptApiKey(AiModelConfig config) {
        if (config == null
                || config.getEncryptedApiKey() == null
                || config.getEncryptedApiKey().isBlank()) {
            throw new BusinessException(
                ErrorCode.AI_NOT_CONFIGURED,
                "尚未配置 AI API Key"
            );
        }
        try {
            return secretCipher.decrypt(config.getEncryptedApiKey());
        } catch (IllegalStateException error) {
            throw new BusinessException(
                ErrorCode.AI_SERVER_MISCONFIGURED,
                "AI 加密配置未就绪"
            );
        }
    }

    private Duration requestTimeout(Integer seconds) {
        if (seconds == null || seconds <= 0) throw upstreamError();
        return Duration.ofSeconds(seconds);
    }

    private URI modelsUri(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException | NullPointerException error) {
            throw upstreamError();
        }
        String path = uri.getRawPath();
        boolean officialOrigin = !uri.isOpaque()
            && "https".equalsIgnoreCase(uri.getScheme())
            && "api.deepseek.com".equalsIgnoreCase(uri.getHost())
            && "api.deepseek.com".equalsIgnoreCase(uri.getRawAuthority())
            && uri.getRawUserInfo() == null
            && uri.getPort() == -1
            && (path == null || path.isEmpty() || "/".equals(path))
            && uri.getRawQuery() == null
            && uri.getRawFragment() == null;
        if (!officialOrigin) throw upstreamError();
        return URI.create(OFFICIAL_BASE_URL + "/models");
    }

    private URI chatUri(String baseUrl) {
        modelsUri(baseUrl);
        return URI.create(OFFICIAL_BASE_URL + "/chat/completions");
    }

    private BusinessException mappedUpstream(int status) {
        if (status == 401 || status == 403) {
            return new BusinessException(
                ErrorCode.AI_UNAUTHORIZED,
                "AI 服务鉴权失败"
            );
        }
        if (status == 400 || status == 404) {
            return new BusinessException(
                ErrorCode.AI_MODEL_UNAVAILABLE,
                "配置的 AI 模型当前不可用"
            );
        }
        if (status == 429) {
            return new BusinessException(
                ErrorCode.AI_RATE_LIMITED,
                "AI 服务请求过于频繁"
            );
        }
        return upstreamError();
    }

    private BusinessException timeout() {
        return new BusinessException(
            ErrorCode.AI_TIMEOUT,
            "AI 服务响应超时"
        );
    }

    private BusinessException upstreamError() {
        return new BusinessException(
            ErrorCode.AI_UPSTREAM_ERROR,
            "AI 上游服务异常"
        );
    }

    private BusinessException responseFormatInvalid() {
        return new BusinessException(
            ErrorCode.AI_RESPONSE_FORMAT_INVALID,
            "AI 返回格式不正确"
        );
    }

    private BusinessException rejectedFormat(String reason) {
        LOGGER.warn("DeepSeek recommendation response rejected: reason={}", reason);
        return responseFormatInvalid();
    }

    private BusinessException resultCountInvalid() {
        return new BusinessException(
            ErrorCode.AI_RESULT_COUNT_INVALID,
            "AI 返回的推荐套数与后台配置不一致"
        );
    }

    private BusinessException resultItemCountInvalid() {
        return new BusinessException(
            ErrorCode.AI_RESULT_ITEM_COUNT_INVALID,
            "AI 返回的每套珠子种类数量必须为 3 至 5 种"
        );
    }

    private BusinessException duplicateSkuInvalid() {
        return new BusinessException(
            ErrorCode.AI_RESULT_DUPLICATE_SKU,
            "AI 在同一套方案中重复选择了珠子"
        );
    }

    private BusinessException skuInvalid() {
        return new BusinessException(
            ErrorCode.AI_RESULT_SKU_INVALID,
            "AI 选择了不存在、下架或无库存的商品"
        );
    }
}
