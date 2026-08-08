package com.diybracelet.mall.catalog.api.dto;

/** 后台商品列表使用的库存预警状态。 */
public enum StockWarningStatus {

    /** 可用库存高于预警值。 */
    NORMAL,

    /** 可用库存已达到或低于预警值，需要补货。 */
    RESTOCK
}
