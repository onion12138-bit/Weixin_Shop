package com.diybracelet.mall.catalog.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 小程序成品手串详情及其组成。
 *
 * @param id 套餐主键
 * @param packageCode 稳定套餐编码
 * @param name 展示名称
 * @param description 完整说明
 * @param salePrice 销售价，单位元
 * @param coverUrl 封面公开地址，可为空
 * @param items 按搭配顺序排列的珠子明细
 */
public record PackageDetail(
        Long id,
        String packageCode,
        String name,
        String description,
        BigDecimal salePrice,
        String coverUrl,
        List<Item> items
) {

    /**
     * 套餐中的一条散珠组成。
     *
     * @param id 明细主键
     * @param beadSkuId 散珠主键
     * @param quantity 使用颗数
     * @param sortOrder 展示顺序
     * @param bead 散珠公开信息
     */
    public record Item(
            Long id,
            Long beadSkuId,
            Integer quantity,
            Integer sortOrder,
            BeadSkuSummary bead
    ) {
    }
}
