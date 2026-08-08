package com.diybracelet.mall.order.api.dto;

import com.diybracelet.mall.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 后台订单组合查询条件。 */
public record AdminOrderQuery(
        String orderNo,
        String contactName,
        String contactPhone,
        OrderStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String paymentMethod,
        boolean ascending
) { }
