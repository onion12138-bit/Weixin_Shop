package com.diybracelet.mall.order.api.dto;

import com.diybracelet.mall.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 后台订单列表与详情共用的完整只读视图。 */
public record AdminOrderResponse(
        Long id,
        String orderNo,
        OrderStatus status,
        Long schemeId,
        BigDecimal beadAmount,
        BigDecimal craftFee,
        BigDecimal cordFee,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        String couponName,
        String contactName,
        String contactPhone,
        String addressDetail,
        String orderNote,
        Product product,
        List<Item> items,
        Payment payment,
        Fulfillment fulfillment,
        List<Operation> operations,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt,
        LocalDateTime shippedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record Product(
            String name,
            String schemeNo,
            BigDecimal wristCircumferenceMm,
            String fitType,
            String cordName,
            Integer beadCount,
            Integer estimatedMinutes,
            String previewUrl
    ) { }

    public record Item(
            Long skuId,
            String skuCode,
            String name,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice
    ) { }

    public record Payment(
            String channel,
            String status,
            BigDecimal amount,
            LocalDateTime paidAt
    ) { }

    public record Fulfillment(
            Long id,
            String status,
            String logisticsCompany,
            String trackingNo,
            LocalDateTime productionStartedAt,
            LocalDateTime productionCompletedAt,
            LocalDateTime shippedAt,
            LocalDateTime completedAt
    ) { }

    public record Operation(
            Long id,
            String actionCode,
            String operatorType,
            String operatorName,
            String content,
            LocalDateTime occurredAt
    ) { }
}
