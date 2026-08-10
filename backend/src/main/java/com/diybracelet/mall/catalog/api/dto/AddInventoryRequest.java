package com.diybracelet.mall.catalog.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 后台为单个商品追加可用库存的请求。 */
public record AddInventoryRequest(
        @NotNull(message = "新增库存数量不能为空")
        @Positive(message = "新增库存数量必须大于零")
        @Max(value = 1_000_000, message = "单次新增库存不能超过 1000000")
        Integer quantity,

        @Size(max = 120, message = "库存备注不能超过 120 个字符")
        String note
) {
}
