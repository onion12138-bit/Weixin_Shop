package com.diybracelet.mall.order.api.dto;

/** 后台订单状态统计。 */
public record AdminOrderStatistics(
        long total,
        long pendingPayment,
        long inProduction,
        long pendingShipment,
        long shipped,
        long completed,
        long cancelled,
        long afterSale,
        long refunded
) { }
