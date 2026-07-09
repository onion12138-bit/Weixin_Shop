package com.diybracelet.mall.diy.api.dto;

import com.diybracelet.mall.diy.domain.OptionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 管理后台保存绳材选项请求。
 *
 * @param code 稳定编码
 * @param name 展示名称
 * @param colorHex 展示色值
 * @param extraFee 绳材附加费
 * @param status 启停状态
 * @param sortOrder 排序值
 */
public record SaveCordOptionRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9-]{2,64}$")
        String code,
        @NotBlank
        @Size(max = 80)
        String name,
        @NotBlank
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
        String colorHex,
        @NotNull @DecimalMin("0.00") BigDecimal extraFee,
        @NotNull OptionStatus status,
        @NotNull Integer sortOrder
) {
}
