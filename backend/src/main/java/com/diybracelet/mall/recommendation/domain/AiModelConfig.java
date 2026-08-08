package com.diybracelet.mall.recommendation.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 唯一的 AI 模型配置。
 *
 * <p>API Key 字段只允许保存 {@code AiSecretCipher} 生成的密文。普通配置视图和日志
 * 不得暴露该字段；明文只允许由管理员主动调用的专用查看接口短暂返回。</p>
 */
@TableName("ai_model_config")
public class AiModelConfig {

    @TableId("id")
    private Long id;

    private String provider;

    private String baseUrl;

    private String modelName;

    private String encryptedApiKey;

    private Boolean enabled;

    private Integer timeoutSeconds;

    private Integer maxOutputTokens;

    private String recommendationPrompt;

    private Integer planCount;

    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public AiModelConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getEncryptedApiKey() {
        return encryptedApiKey;
    }

    public void setEncryptedApiKey(String encryptedApiKey) {
        this.encryptedApiKey = encryptedApiKey;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public String getRecommendationPrompt() {
        return recommendationPrompt;
    }

    public void setRecommendationPrompt(String recommendationPrompt) {
        this.recommendationPrompt = recommendationPrompt;
    }

    public Integer getPlanCount() {
        return planCount;
    }

    public void setPlanCount(Integer planCount) {
        this.planCount = planCount;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
