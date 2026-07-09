package com.diybracelet.mall.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 小程序匿名访客会话交换请求。
 *
 * <p>{@code installationId} 由小程序首次启动时生成并保存在本地，只用于找到同一安装
 * 身份对应的顾客主键。后端不会把该值原样存库或写回响应。</p>
 *
 * @param installationId 小程序安装级 UUID
 */
public record GuestSessionRequest(
        @NotBlank(message = "安装标识不能为空")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "安装标识格式不正确"
        )
        String installationId
) {
}
