package com.diybracelet.mall.diy.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台整体替换工艺费区间请求。
 *
 * @param rules 新的完整区间集合
 */
public record SaveCraftFeeRulesRequest(
        @NotEmpty List<@NotNull @Valid CraftFeeRuleInput> rules
) {

    /**
     * 单个工艺费区间输入。
     */
    public record CraftFeeRuleInput(
            @NotNull @Min(1) Integer minBeadCount,
            @NotNull @Min(1) Integer maxBeadCount,
            @NotNull @DecimalMin("0.00") BigDecimal fee
    ) {
    }
}
