package com.diybracelet.mall.order.api.dto;

import com.diybracelet.mall.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台订单视图。
 *
 * <p>一次返回订单主信息、生产 BOM 和履约状态，减少后台列表展开详情时的额外请求。</p>
 */
public record AdminOrderResponse(
        Long id,
        String orderNo,
        OrderStatus status,
        Long schemeId,
        BigDecimal totalAmount,
        String contactName,
        String contactPhone,
        String addressDetail,
        List<Item> items,
        Fulfillment fulfillment,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 制作 BOM 中按 SKU 聚合的订单明细。
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

    /**
     * 履约任务摘要。待支付或已取消订单可能没有履约任务。
     */
    public record Fulfillment(
            Long id,
            String status,
            String logisticsCompany,
            String trackingNo,
            LocalDateTime shippedAt,
            LocalDateTime completedAt
    ) {
    }
}
