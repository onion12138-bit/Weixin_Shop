package com.diybracelet.mall.catalog.api.dto;

import com.diybracelet.mall.catalog.domain.CatalogStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 后台新建或修改成品手串的请求。
 */
public record SavePackageRequest(
        @NotBlank(message = "套餐编码不能为空")
        @Size(max = 64, message = "套餐编码不能超过 64 个字符")
        @Pattern(
                regexp = "[A-Z0-9]+(?:-[A-Z0-9]+)*",
                message = "套餐编码只能包含大写字母、数字和连字符"
        )
        String packageCode,

        @NotBlank(message = "套餐名称不能为空")
        @Size(max = 120, message = "套餐名称不能超过 120 个字符")
        String name,

        @NotBlank(message = "套餐说明不能为空")
        @Size(max = 1000, message = "套餐说明不能超过 1000 个字符")
        String description,

        @NotNull(message = "销售价不能为空")
        @DecimalMin(value = "0.00", message = "销售价不能为负数")
        BigDecimal salePrice,

        @Positive(message = "封面素材主键必须大于零")
        Long coverAssetId,

        @NotNull(message = "套餐状态不能为空")
        CatalogStatus status,

        @NotEmpty(message = "套餐至少需要一种散珠")
        List<@Valid SavePackageItemRequest> items
) {
}
