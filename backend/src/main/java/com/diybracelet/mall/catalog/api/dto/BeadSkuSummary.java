package com.diybracelet.mall.catalog.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 小程序散珠列表项。
 *
 * <p>公开模型有意不包含成本价和库存预警阈值，避免后台经营数据泄露。</p>
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
 * @param tags 展示标签
 */
public record BeadSkuSummary(
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
}
