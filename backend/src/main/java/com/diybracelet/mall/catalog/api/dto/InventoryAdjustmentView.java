package com.diybracelet.mall.catalog.api.dto;

/** 后台新增库存完成后的库存余额。 */
public record InventoryAdjustmentView(
        Long beadSkuId,
        Integer addedQuantity,
        Integer availableQty,
        Integer reservedQty,
        Integer soldQty
) {
}
