package com.diybracelet.mall.recommendation.api.dto;

/**
 * 管理员主动点击查看时返回的 API Key 明文。
 *
 * <p>该视图只允许从受管理员 JWT 保护的专用接口返回，不得写入日志、普通配置响应
 * 或持久化为明文。</p>
 */
public record AiApiKeyView(String apiKey) {
    @Override
    public String toString() {
        return "AiApiKeyView[apiKeyRedacted=true]";
    }
}
