package com.diybracelet.mall.catalog.api.dto;

import com.diybracelet.mall.catalog.domain.CatalogStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 后台新建散珠 SKU 的请求，包含只在创建时使用的初始库存。
 */
public record CreateBeadSkuRequest(
        @NotNull(message = "分类不能为空")
        @Positive(message = "分类主键必须大于零")
        Long categoryId,

        @NotBlank(message = "SKU 编码不能为空")
        @Size(max = 64, message = "SKU 编码不能超过 64 个字符")
        @Pattern(
                regexp = "[A-Z0-9]+(?:-[A-Z0-9]+)*",
                message = "SKU 编码只能包含大写字母、数字和连字符"
        )
        String skuCode,

        @NotBlank(message = "散珠名称不能为空")
        @Size(max = 120, message = "散珠名称不能超过 120 个字符")
        String name,

        @NotBlank(message = "材质不能为空")
        @Size(max = 80, message = "材质不能超过 80 个字符")
        String material,

        @NotBlank(message = "颜色不能为空")
        @Size(max = 80, message = "颜色不能超过 80 个字符")
        String color,

        @DecimalMin(value = "0.01", message = "珠径必须大于零")
        BigDecimal diameterMm,

        @NotNull(message = "销售价不能为空")
        @DecimalMin(value = "0.00", message = "销售价不能为负数")
        BigDecimal salePrice,

        @NotNull(message = "成本价不能为空")
        @DecimalMin(value = "0.00", message = "成本价不能为负数")
        BigDecimal costPrice,

        @NotNull(message = "库存预警阈值不能为空")
        @PositiveOrZero(message = "库存预警阈值不能为负数")
        Integer stockAlertThreshold,

        @Positive(message = "封面素材主键必须大于零")
        Long coverAssetId,

        @NotNull(message = "标签不能为空")
        @Size(max = 20, message = "标签不能超过 20 个")
        List<
                @NotBlank(message = "标签不能为空")
                @Size(max = 30, message = "单个标签不能超过 30 个字符")
                String
        > tags,

        @NotNull(message = "散珠状态不能为空")
        CatalogStatus status,

        @NotNull(message = "初始库存不能为空")
        @PositiveOrZero(message = "初始库存不能为负数")
        Integer initialStock
) {

    public SaveBeadSkuRequest bead() {
        return new SaveBeadSkuRequest(
                categoryId,
                skuCode,
                name,
                material,
                color,
                diameterMm,
                salePrice,
                costPrice,
                stockAlertThreshold,
                coverAssetId,
                tags,
                status
        );
    }
}
