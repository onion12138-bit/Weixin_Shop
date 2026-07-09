package com.diybracelet.mall.diy.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 管理后台保存 DIY 尺寸规则请求。
 *
 * @param minWristMm 最小腕围，毫米
 * @param maxWristMm 最大腕围，毫米
 * @param snugAllowanceMm 贴合松紧余量，毫米
 * @param standardAllowanceMm 标准松紧余量，毫米
 * @param looseAllowanceMm 宽松松紧余量，毫米
 * @param toleranceMm 允许误差，毫米
 * @param maxBeadCount 最大业务珠数
 */
public record SaveDiyRuleConfigRequest(
        @NotNull @DecimalMin("0.00") BigDecimal minWristMm,
        @NotNull @DecimalMin("0.00") BigDecimal maxWristMm,
        @NotNull @DecimalMin("0.00") BigDecimal snugAllowanceMm,
        @NotNull @DecimalMin("0.00") BigDecimal standardAllowanceMm,
        @NotNull @DecimalMin("0.00") BigDecimal looseAllowanceMm,
        @NotNull @DecimalMin("0.00") BigDecimal toleranceMm,
        @NotNull @Min(1) @Max(200) Integer maxBeadCount
) {
}
