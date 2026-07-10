package com.diybracelet.mall.order.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.order.api.dto.AdminOrderResponse;
import com.diybracelet.mall.order.api.dto.ShipOrderRequest;
import com.diybracelet.mall.order.domain.CustomerOrder;
import com.diybracelet.mall.order.domain.FulfillmentTask;
import com.diybracelet.mall.order.domain.OrderItem;
import com.diybracelet.mall.order.domain.OrderStatus;
import com.diybracelet.mall.order.infrastructure.mapper.CustomerOrderMapper;
import com.diybracelet.mall.order.infrastructure.mapper.FulfillmentTaskMapper;
import com.diybracelet.mall.order.infrastructure.mapper.OrderItemMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台订单查询与履约应用服务。
 *
 * <p>订单状态和履约任务状态必须同步前进。每个动作先锁定两行并校验前置状态，
 * 再在同一事务内更新，任一更新失败都会整体回滚。</p>
 */
@Service
public class AdminOrderService {

    private final CustomerOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final FulfillmentTaskMapper fulfillmentMapper;

    public AdminOrderService(
            CustomerOrderMapper orderMapper,
            OrderItemMapper itemMapper,
            FulfillmentTaskMapper fulfillmentMapper
    ) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.fulfillmentMapper = fulfillmentMapper;
    }

    /**
     * 按状态分页查询订单，状态为空时返回全部订单。
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> list(
            OrderStatus status,
            int page,
            int size
    ) {
        LambdaQueryWrapper<CustomerOrder> query =
                new LambdaQueryWrapper<>();
        if (status != null) {
            query.eq(CustomerOrder::getStatus, status);
        }
        long total = orderMapper.selectCount(query);
        long offset = (long) (page - 1) * size;
        query.orderByDesc(CustomerOrder::getId)
                .last("limit " + size + " offset " + offset);
        List<AdminOrderResponse> items = orderMapper.selectList(query)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(items, total, page, size);
    }

    /**
     * 查询单个后台订单详情。
     */
    @Transactional(readOnly = true)
    public AdminOrderResponse get(Long orderId) {
        CustomerOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw notFound();
        }
        return toResponse(order);
    }

    /**
     * 把已支付订单和待制作任务同步切换为制作中。
     */
    @Transactional
    public AdminOrderResponse startProduction(Long orderId) {
        LockedFulfillment locked = lock(orderId);
        requireState(
                locked,
                OrderStatus.PAID,
                "PENDING",
                "只有已支付订单可以开始制作"
        );
        if (orderMapper.markInProduction(orderId) != 1
                || fulfillmentMapper.startProduction(orderId) != 1) {
            throw stateConflict("开始制作失败，订单状态已变化");
        }
        return get(orderId);
    }

    /**
     * 把制作中订单发货，并保存可供前端展示的模拟物流信息。
     */
    @Transactional
    public AdminOrderResponse ship(
            Long orderId,
            ShipOrderRequest request
    ) {
        LockedFulfillment locked = lock(orderId);
        requireState(
                locked,
                OrderStatus.IN_PRODUCTION,
                "IN_PRODUCTION",
                "只有制作中订单可以发货"
        );
        LocalDateTime shippedAt = LocalDateTime.now();
        if (orderMapper.markShipped(orderId, shippedAt) != 1
                || fulfillmentMapper.ship(
                orderId,
                request.logisticsCompany().trim(),
                request.trackingNo().trim(),
                shippedAt
        ) != 1) {
            throw stateConflict("发货失败，订单状态已变化");
        }
        return get(orderId);
    }

    /**
     * 把已发货订单和履约任务同步标记为完成。
     */
    @Transactional
    public AdminOrderResponse complete(Long orderId) {
        LockedFulfillment locked = lock(orderId);
        requireState(
                locked,
                OrderStatus.SHIPPED,
                "SHIPPED",
                "只有已发货订单可以完成"
        );
        LocalDateTime completedAt = LocalDateTime.now();
        if (orderMapper.markCompleted(orderId, completedAt) != 1
                || fulfillmentMapper.complete(orderId, completedAt) != 1) {
            throw stateConflict("完成订单失败，订单状态已变化");
        }
        return get(orderId);
    }

    private LockedFulfillment lock(Long orderId) {
        CustomerOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw notFound();
        }
        FulfillmentTask fulfillment =
                fulfillmentMapper.selectByOrderIdForUpdate(orderId);
        if (fulfillment == null) {
            throw stateConflict("订单尚未生成履约任务");
        }
        return new LockedFulfillment(order, fulfillment);
    }

    private void requireState(
            LockedFulfillment locked,
            OrderStatus expectedOrderStatus,
            String expectedFulfillmentStatus,
            String message
    ) {
        if (locked.order().getStatus() != expectedOrderStatus
                || !expectedFulfillmentStatus.equals(
                locked.fulfillment().getStatus()
        )) {
            throw stateConflict(message);
        }
    }

    private AdminOrderResponse toResponse(CustomerOrder order) {
        List<OrderItem> orderItems = itemMapper.selectByOrderId(order.getId());
        FulfillmentTask fulfillment =
                fulfillmentMapper.selectByOrderId(order.getId());
        return new AdminOrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getDiySchemeId(),
                order.getTotalAmount(),
                order.getContactName(),
                order.getContactPhone(),
                order.getAddressDetail(),
                orderItems.stream().map(item -> new AdminOrderResponse.Item(
                        item.getBeadSkuId(),
                        item.getSkuCode(),
                        item.getName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalPrice()
                )).toList(),
                toFulfillment(fulfillment),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getCompletedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private AdminOrderResponse.Fulfillment toFulfillment(
            FulfillmentTask task
    ) {
        if (task == null) {
            return null;
        }
        return new AdminOrderResponse.Fulfillment(
                task.getId(),
                task.getStatus(),
                task.getLogisticsCompany(),
                task.getTrackingNo(),
                task.getShippedAt(),
                task.getCompletedAt()
        );
    }

    private BusinessException notFound() {
        return new BusinessException(
                ErrorCode.COMMON_NOT_FOUND,
                "订单不存在"
        );
    }

    private BusinessException stateConflict(String message) {
        return new BusinessException(ErrorCode.COMMON_CONFLICT, message);
    }

    /**
     * 同一事务中已锁定的订单和履约任务组合。
     */
    private record LockedFulfillment(
            CustomerOrder order,
            FulfillmentTask fulfillment
    ) {
    }
}
