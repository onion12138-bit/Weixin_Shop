package com.diybracelet.mall.order.domain;

/**
 * 顾客订单状态。
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    IN_PRODUCTION,
    SHIPPED,
    COMPLETED,
    CANCELLED
}
