package com.diybracelet.mall.catalog.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 后台成品手串中的一条散珠组成。
 */
public record SavePackageItemRequest(
        @NotNull(message = "散珠不能为空")
        @Positive(message = "散珠主键必须大于零")
        Long beadSkuId,

        @NotNull(message = "使用颗数不能为空")
        @Positive(message = "使用颗数必须大于零")
        Integer quantity
) {
}
