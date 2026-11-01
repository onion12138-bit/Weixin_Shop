package com.diybracelet.mall.catalog.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 小程序散珠详情。
 *
 * @param id 散珠主键
 * @param categoryId 所属分类主键
 * @param skuCode 稳定 SKU 编码
 * @param name 展示名称
 * @param material 材质
 * @param color 颜色
 * @param diameterMm 珠径，单位毫米
 * @param salePrice 销售价，单位元
 * @param coverUrl 封面公开地址，可为空
 * @param availableQty 当前可用库存，仅供提示，下单时会再次校验并预占
 * @param tags 展示标签
 */
public record BeadSkuDetail(
        Long id,
        Long categoryId,
        String skuCode,
        String name,
        String material,
        String color,
        BigDecimal diameterMm,
        BigDecimal salePrice,
        String coverUrl,
        List<String> tags,
        DiyVisualView diyVisual,
        int availableQty
) {

    public BeadSkuDetail(
            Long id,
            Long categoryId,
            String skuCode,
            String name,
            String material,
            String color,
            BigDecimal diameterMm,
            BigDecimal salePrice,
            String coverUrl,
            List<String> tags
    ) {
        this(
                id, categoryId, skuCode, name, material, color, diameterMm,
                salePrice, coverUrl, tags, null, 0
        );
    }
}
