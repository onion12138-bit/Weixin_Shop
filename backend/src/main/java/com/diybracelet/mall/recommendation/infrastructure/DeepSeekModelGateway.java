package com.diybracelet.mall.recommendation.infrastructure;

import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelGateway;
import com.diybracelet.mall.recommendation.domain.ModelRecommendation;
import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.recommendation.domain.RecommendationProfile;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Java 17 HTTP adapter for DeepSeek model discovery and strict JSON chat. */
@Component
public class DeepSeekModelGateway implements AiModelGateway {

    private static final String OFFICIAL_BASE_URL = "https://api.deepseek.com";
    private static final int MAX_SEQUENCE_SIZE = 200;
    private static final Set<String> MODEL_FIELDS = Set.of(
        "schemeName", "sequence", "reasons", "summary"
    );
    private static final Set<String> REASON_FIELDS = Set.of("skuId", "reason");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
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
        } catch (RuntimeException error) {
            throw temporarilyUnavailable();
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw temporarilyUnavailable();
        } catch (IOException | RuntimeException error) {
            throw temporarilyUnavailable();
        }

        return handleResponse(response);
    }

    @Override
    public ModelRecommendation recommend(
            AiModelConfig config,
            RecommendationProfile profile,
            List<RecommendationCandidate> candidates
    ) {
        String apiKey = decryptApiKey(config);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(chatUri(config.getBaseUrl()))
                .timeout(requestTimeout(config.getTimeoutSeconds()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                    chatRequestJson(config, profile, candidates)
                ))
                .build();
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException error) {
            throw temporarilyUnavailable();
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw temporarilyUnavailable();
        } catch (IOException | RuntimeException error) {
            throw temporarilyUnavailable();
        }
        return handleChatResponse(response, candidates);
    }

    private String chatRequestJson(
            AiModelConfig config,
            RecommendationProfile profile,
            List<RecommendationCandidate> candidates
    ) {
        if (config == null
                || config.getModelName() == null
                || config.getModelName().isBlank()
                || config.getMaxOutputTokens() == null
                || config.getMaxOutputTokens() <= 0
                || profile == null
                || candidates == null
                || candidates.isEmpty()) {
            throw temporarilyUnavailable();
        }

        ObjectNode prompt = objectMapper.createObjectNode();
        prompt.set("profile", profileNode(profile));
        ArrayNode candidateNodes = prompt.putArray("candidates");
        for (RecommendationCandidate candidate : candidates) {
            candidateNodes.add(candidateNode(candidate));
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", config.getModelName());
        request.put("temperature", new BigDecimal("0.2"));
        request.put("max_tokens", config.getMaxOutputTokens());
        request.putObject("response_format").put("type", "json_object");
        ArrayNode messages = request.putArray("messages");
        messages.addObject()
            .put("role", "system")
            .put(
                "content",
                "你是手串搭配助手。只能选择候选 SKU，输出且仅输出 JSON 对象："
                    + "{\"schemeName\":\"string\",\"sequence\":[2001],"
                    + "\"reasons\":[{\"skuId\":2001,\"reason\":\"string\"}],"
                    + "\"summary\":\"string\"}。不要输出价格、数量字段或确定效果承诺。"
            );
        try {
            messages.addObject()
                .put("role", "user")
                .put("content", objectMapper.writeValueAsString(prompt));
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException error) {
            throw temporarilyUnavailable();
        }
    }

    private ObjectNode profileNode(RecommendationProfile profile) {
        ObjectNode node = objectMapper.createObjectNode();
        putOptional(node, "constellation", profile.constellation());
        putOptional(node, "zodiac", profile.zodiac());
        putOptional(node, "elementPreference", profile.elementPreference());
        putOptional(node, "wish", profile.wish());
        putOptional(node, "scene", profile.scene());
        putOptional(node, "colorPreference", profile.colorPreference());
        node.put("budget", profile.budget());
        node.put("wristCircumferenceMm", profile.wristCircumferenceMm());
        node.put("fitType", profile.fitType().name());
        putOptional(node, "freeText", profile.freeText());
        return node;
    }

    private ObjectNode candidateNode(RecommendationCandidate candidate) {
        if (candidate == null
                || candidate.skuId() == null
                || candidate.skuId() <= 0
                || candidate.availableQty() <= 0) {
            throw invalidRecommendation();
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("skuId", candidate.skuId());
        node.put("name", candidate.name());
        node.put("material", candidate.material());
        node.put("color", candidate.color());
        node.put("diameterMm", candidate.diameterMm());
        node.put("salePrice", candidate.salePrice());
        ArrayNode tags = node.putArray("tags");
        try {
            JsonNode storedTags = objectMapper.readTree(candidate.tagsJson());
            if (storedTags == null || !storedTags.isArray()) {
                throw invalidRecommendation();
            }
            for (JsonNode tag : storedTags) {
                if (!tag.isTextual() || tag.textValue().length() > 80) {
                    throw invalidRecommendation();
                }
                tags.add(tag.textValue());
            }
        } catch (JsonProcessingException error) {
            throw invalidRecommendation();
        }
        node.put("availableQty", candidate.availableQty());
        return node;
    }

    private void putOptional(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private ModelRecommendation handleChatResponse(
            HttpResponse<String> response,
            List<RecommendationCandidate> candidates
    ) {
        int status = response.statusCode();
        if (status == 200) {
            return parseRecommendation(response.body(), candidates);
        }
        if (status == 401 || status == 403) {
            throw new BusinessException(
                ErrorCode.AI_UNAUTHORIZED,
                "AI 服务鉴权失败"
            );
        }
        if (status == 400 || status == 404) {
            throw new BusinessException(
                ErrorCode.AI_MODEL_UNAVAILABLE,
                "配置的 AI 模型当前不可用"
            );
        }
        if (status == 429) {
            throw new BusinessException(
                ErrorCode.AI_RATE_LIMITED,
                "AI 服务请求过于频繁"
            );
        }
        throw temporarilyUnavailable();
    }

    private ModelRecommendation parseRecommendation(
            String body,
            List<RecommendationCandidate> candidates
    ) {
        try {
            JsonNode envelope = body == null ? null : objectMapper.readTree(body);
            JsonNode choices = envelope == null ? null : envelope.get("choices");
            if (choices == null || !choices.isArray() || choices.size() < 1) {
                throw invalidRecommendation();
            }
            JsonNode content = choices.get(0).path("message").get("content");
            if (content == null || !content.isTextual()) {
                throw invalidRecommendation();
            }
            JsonNode root = objectMapper.readTree(content.textValue());
            if (!exactFields(root, MODEL_FIELDS)) {
                throw invalidRecommendation();
            }
            String schemeName = strictText(root.get("schemeName"), 80);
            String summary = strictText(root.get("summary"), 1000);
            Set<Long> candidateIds = candidates.stream()
                .map(RecommendationCandidate::skuId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            JsonNode sequenceNode = root.get("sequence");
            if (sequenceNode == null
                    || !sequenceNode.isArray()
                    || sequenceNode.isEmpty()
                    || sequenceNode.size() > MAX_SEQUENCE_SIZE) {
                throw invalidRecommendation();
            }
            List<Long> sequence = new ArrayList<>();
            Set<Long> selectedIds = new HashSet<>();
            for (JsonNode item : sequenceNode) {
                if (!item.isIntegralNumber() || !item.canConvertToLong()) {
                    throw invalidRecommendation();
                }
                long skuId = item.longValue();
                if (skuId <= 0 || !candidateIds.contains(skuId)) {
                    throw invalidRecommendation();
                }
                sequence.add(skuId);
                selectedIds.add(skuId);
            }

            JsonNode reasonsNode = root.get("reasons");
            if (reasonsNode == null || !reasonsNode.isArray() || reasonsNode.isEmpty()) {
                throw invalidRecommendation();
            }
            List<ModelRecommendation.Reason> reasons = new ArrayList<>();
            Set<Long> reasonIds = new HashSet<>();
            for (JsonNode item : reasonsNode) {
                if (!exactFields(item, REASON_FIELDS)) {
                    throw invalidRecommendation();
                }
                JsonNode skuNode = item.get("skuId");
                if (!skuNode.isIntegralNumber() || !skuNode.canConvertToLong()) {
                    throw invalidRecommendation();
                }
                long skuId = skuNode.longValue();
                if (!selectedIds.contains(skuId) || !reasonIds.add(skuId)) {
                    throw invalidRecommendation();
                }
                reasons.add(new ModelRecommendation.Reason(
                    skuId,
                    strictText(item.get("reason"), 300)
                ));
            }
            if (!reasonIds.equals(selectedIds)) {
                throw invalidRecommendation();
            }
            return new ModelRecommendation(schemeName, sequence, reasons, summary);
        } catch (BusinessException error) {
            throw error;
        } catch (JsonProcessingException | RuntimeException error) {
            throw invalidRecommendation();
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

    private String strictText(JsonNode node, int maxLength) {
        if (node == null || !node.isTextual()) {
            throw invalidRecommendation();
        }
        String value = node.textValue().trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw invalidRecommendation();
        }
        return value;
    }

    private List<String> handleResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 200) {
            return parseModels(response.body());
        }
        if (status == 401 || status == 403) {
            throw new BusinessException(
                ErrorCode.AI_UNAUTHORIZED,
                "AI 服务鉴权失败"
            );
        }
        if (status == 429) {
            throw new BusinessException(
                ErrorCode.AI_RATE_LIMITED,
                "AI 服务请求过于频繁"
            );
        }
        throw temporarilyUnavailable();
    }

    private List<String> parseModels(String body) {
        try {
            JsonNode root = body == null ? null : objectMapper.readTree(body);
            JsonNode data = root == null ? null : root.get("data");
            if (data == null || !data.isArray()) {
                throw temporarilyUnavailable();
            }
            List<String> models = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode id = item.get("id");
                if (id != null && id.isTextual() && !id.textValue().isBlank()) {
                    models.add(id.textValue());
                }
            }
            return List.copyOf(models);
        } catch (JsonProcessingException error) {
            throw temporarilyUnavailable();
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
            throw temporarilyUnavailable();
        }
    }

    private Duration requestTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            throw temporarilyUnavailable();
        }
        return Duration.ofSeconds(timeoutSeconds);
    }

    private URI modelsUri(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException | NullPointerException error) {
            throw temporarilyUnavailable();
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
        if (!officialOrigin) {
            throw temporarilyUnavailable();
        }
        return URI.create(OFFICIAL_BASE_URL + "/models");
    }

    private URI chatUri(String baseUrl) {
        URI models = modelsUri(baseUrl);
        if (!models.toString().equals(OFFICIAL_BASE_URL + "/models")) {
            throw temporarilyUnavailable();
        }
        return URI.create(OFFICIAL_BASE_URL + "/chat/completions");
    }

    private BusinessException invalidRecommendation() {
        return new BusinessException(
            ErrorCode.AI_INVALID_RECOMMENDATION,
            "AI 推荐未通过校验"
        );
    }

    private BusinessException temporarilyUnavailable() {
        return new BusinessException(
            ErrorCode.AI_TEMPORARILY_UNAVAILABLE,
            "AI 服务暂时不可用"
        );
    }
}
