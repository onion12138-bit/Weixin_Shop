package com.diybracelet.mall.recommendation.infrastructure;

import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelGateway;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Java 17 HTTP adapter for the DeepSeek model-list endpoint. */
@Component
public class DeepSeekModelGateway implements AiModelGateway {

    private static final String OFFICIAL_BASE_URL = "https://api.deepseek.com";

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

    private BusinessException temporarilyUnavailable() {
        return new BusinessException(
            ErrorCode.AI_TEMPORARILY_UNAVAILABLE,
            "AI 服务暂时不可用"
        );
    }
}
