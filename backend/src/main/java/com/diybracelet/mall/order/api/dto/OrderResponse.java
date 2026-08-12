package com.diybracelet.mall.order.api.dto;

import com.diybracelet.mall.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 小程序订单响应。
 */
public record OrderResponse(
        Long id,
        String orderNo,
        OrderStatus status,
        Long schemeId,
        BigDecimal beadAmount,
        BigDecimal craftFee,
        BigDecimal cordFee,
        Long couponId,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        String contactName,
        String contactPhone,
        String addressDetail,
        List<Item> items,
        String logisticsCompany,
        String trackingNo,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 订单 SKU 聚合明细。
     */
    public record Item(
            Long skuId,
            String skuCode,
            String name,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice
    ) {
    }
}
