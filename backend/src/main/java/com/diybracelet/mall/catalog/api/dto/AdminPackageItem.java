package com.diybracelet.mall.catalog.api.dto;

/**
 * 管理后台编辑套餐时使用的有序散珠明细。
 *
 * @param beadSkuId 散珠 SKU 主键
 * @param quantity 使用数量
 * @param sortOrder 持久化后的显示顺序
 */
public record AdminPackageItem(
        Long beadSkuId,
        Integer quantity,
        Integer sortOrder
) {
}
