package com.diybracelet.mall.identity.api.dto;

/**
 * 匿名访客会话交换响应。
 *
 * @param customerIdentityId 后端内部顾客身份主键
 * @param accessToken 顾客侧 Bearer JWT
 * @param expiresInSeconds 从签发起计算的有效秒数
 */
public record GuestSessionResponse(
        Long customerIdentityId,
        String accessToken,
        long expiresInSeconds
) {
}
