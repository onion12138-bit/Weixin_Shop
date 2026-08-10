package com.diybracelet.mall.recommendation.application;

import com.diybracelet.mall.recommendation.api.dto.AiApiKeyView;
import com.diybracelet.mall.recommendation.api.dto.AiConfigurationView;
import com.diybracelet.mall.recommendation.api.dto.SaveAiConfigurationRequest;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelGateway;
import com.diybracelet.mall.recommendation.infrastructure.AiModelConfigMapper;
import com.diybracelet.mall.recommendation.infrastructure.AiSecretCipher;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintains the singleton AI configuration and its explicit admin-only reveal flow. */
@Service
public class AiConfigurationService {

    private static final long SINGLETON_ID = 1L;
    private static final String PROVIDER = "DEEPSEEK";
    private static final String OFFICIAL_BASE_URL = "https://api.deepseek.com";
    private static final String FIXED_MODEL_NAME = "deepseek-v4-flash";

    private final AiModelConfigMapper configMapper;
    private final AiSecretCipher secretCipher;
    private final AiModelGateway modelGateway;

    public AiConfigurationService(
            AiModelConfigMapper configMapper,
            AiSecretCipher secretCipher,
            AiModelGateway modelGateway
    ) {
        this.configMapper = configMapper;
        this.secretCipher = secretCipher;
        this.modelGateway = modelGateway;
    }

    /** Loads the only public administration representation of the configuration. */
    @Transactional(readOnly = true)
    public AiConfigurationView getConfiguration() {
        return toView(requireConfiguration());
    }

    /** Decrypts the stored key only for the authenticated, explicit reveal endpoint. */
    @Transactional(readOnly = true)
    public AiApiKeyView revealApiKey() {
        AiModelConfig config = requireConfiguration();
        if (!hasConfiguredKey(config)) {
            throw new BusinessException(
                ErrorCode.AI_NOT_CONFIGURED,
                "尚未配置 AI API Key"
            );
        }
        try {
            return new AiApiKeyView(
                secretCipher.decrypt(config.getEncryptedApiKey())
            );
        } catch (IllegalStateException error) {
            throw new BusinessException(
                ErrorCode.AI_SERVER_MISCONFIGURED,
                "AI 加密配置未就绪，请重新启动管理后台"
            );
        }
    }

    /** Replaces settings with a version guard; a blank key preserves the stored key. */
    @Transactional
    public AiConfigurationView save(SaveAiConfigurationRequest request) {
        AiModelConfig current = requireConfiguration();
        ensureCurrentVersion(current, request.version());

        AiModelConfig replacement = new AiModelConfig();
        replacement.setId(SINGLETON_ID);
        replacement.setProvider(PROVIDER);
        replacement.setBaseUrl(validateAndNormalizeBaseUrl(request.baseUrl()));
        replacement.setModelName(validateFixedModelName(request.modelName()));
        replacement.setEncryptedApiKey(encryptedReplacementKey(request.apiKey(), current));
        replacement.setEnabled(request.enabled());
        replacement.setTimeoutSeconds(request.timeoutSeconds());
        replacement.setMaxOutputTokens(request.maxOutputTokens());
        replacement.setRecommendationPrompt(request.recommendationPrompt().trim());
        replacement.setPreferencePrompt(request.preferencePrompt().trim());
        replacement.setSoftPreferenceEnabled(request.softPreferenceEnabled());
        replacement.setPlanCount(request.planCount());

        if (configMapper.updateWithVersion(replacement, request.version()) != 1) {
            throw versionConflict();
        }
        return getConfiguration();
    }

    /** Clears the stored key only when the caller supplies the current version. */
    @Transactional
    public AiConfigurationView clearApiKey(long version) {
        AiModelConfig current = requireConfiguration();
        ensureCurrentVersion(current, version);
        current.setEncryptedApiKey(null);
        if (configMapper.updateWithVersion(current, version) != 1) {
            throw versionConflict();
        }
        return getConfiguration();
    }

    /** Verifies provider authentication and that the configured model still exists. */
    public void testConnection() {
        AiModelConfig config = requireConfiguration();
        if (!hasConfiguredKey(config)) {
            throw new BusinessException(
                ErrorCode.AI_NOT_CONFIGURED,
                "尚未配置 AI API Key"
            );
        }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(
                ErrorCode.AI_DISABLED,
                "AI 服务已停用"
            );
        }

        config.setBaseUrl(validateAndNormalizeBaseUrl(config.getBaseUrl()));
        List<String> models = modelGateway.listModels(config);
        if (models == null || !models.contains(config.getModelName())) {
            throw new BusinessException(
                ErrorCode.AI_MODEL_UNAVAILABLE,
                "配置的 AI 模型当前不可用"
            );
        }
    }

    private String encryptedReplacementKey(String requestedKey, AiModelConfig current) {
        if (requestedKey == null || requestedKey.isBlank()) {
            return current.getEncryptedApiKey();
        }
        try {
            return secretCipher.encrypt(requestedKey.trim());
        } catch (IllegalStateException error) {
            throw new BusinessException(
                ErrorCode.AI_SERVER_MISCONFIGURED,
                "AI 加密配置未就绪，请重新启动管理后台"
            );
        }
    }

    private String validateFixedModelName(String modelName) {
        if (!FIXED_MODEL_NAME.equals(modelName)) {
            throw badRequest("AI 模型仅允许 deepseek-v4-flash");
        }
        return FIXED_MODEL_NAME;
    }

    private String validateAndNormalizeBaseUrl(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw badRequest("AI Base URL 不合法");
        }

        URI uri;
        try {
            uri = new URI(candidate.trim());
        } catch (URISyntaxException error) {
            throw badRequest("AI Base URL 不合法");
        }

        String path = uri.getRawPath();
        boolean pathAllowed = path == null || path.isEmpty() || "/".equals(path);
        boolean officialOrigin = !uri.isOpaque()
            && "https".equalsIgnoreCase(uri.getScheme())
            && "api.deepseek.com".equalsIgnoreCase(uri.getHost())
            && "api.deepseek.com".equalsIgnoreCase(uri.getRawAuthority())
            && uri.getRawUserInfo() == null
            && uri.getPort() == -1
            && pathAllowed
            && uri.getRawQuery() == null
            && uri.getRawFragment() == null;
        if (!officialOrigin) {
            throw badRequest("AI Base URL 仅允许 DeepSeek 官方地址");
        }
        return OFFICIAL_BASE_URL;
    }

    private AiModelConfig requireConfiguration() {
        AiModelConfig config = configMapper.selectById(SINGLETON_ID);
        if (config == null) {
            throw new BusinessException(
                ErrorCode.COMMON_INTERNAL_ERROR,
                "AI 配置缺失"
            );
        }
        return config;
    }

    private void ensureCurrentVersion(AiModelConfig current, long requestedVersion) {
        if (current.getVersion() == null || current.getVersion() != requestedVersion) {
            throw versionConflict();
        }
    }

    private boolean hasConfiguredKey(AiModelConfig config) {
        return config.getEncryptedApiKey() != null
            && !config.getEncryptedApiKey().isBlank();
    }

    private AiConfigurationView toView(AiModelConfig config) {
        return new AiConfigurationView(
            config.getProvider(),
            config.getBaseUrl(),
            config.getModelName(),
            hasConfiguredKey(config),
            Boolean.TRUE.equals(config.getEnabled()),
            config.getTimeoutSeconds(),
            config.getMaxOutputTokens(),
            config.getRecommendationPrompt(),
            config.getPreferencePrompt(),
            Boolean.TRUE.equals(config.getSoftPreferenceEnabled()),
            config.getPlanCount(),
            config.getVersion()
        );
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.COMMON_BAD_REQUEST, message);
    }

    private BusinessException versionConflict() {
        return new BusinessException(
            ErrorCode.COMMON_CONFLICT,
            "AI 配置已被其他页面更新，请重新载入"
        );
    }
}
