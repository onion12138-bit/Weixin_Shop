package com.diybracelet.mall.order.domain;

/**
 * 顾客订单状态。
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    IN_PRODUCTION,
    PENDING_SHIPMENT,
    SHIPPED,
    COMPLETED,
    CANCELLED,
    AFTER_SALE,
    REFUNDED
}
