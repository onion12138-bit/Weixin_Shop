package com.diybracelet.mall.order.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.customer.domain.CouponTemplate;
import com.diybracelet.mall.customer.domain.CustomerCoupon;
import com.diybracelet.mall.customer.infrastructure.CouponTemplateMapper;
import com.diybracelet.mall.customer.infrastructure.CustomerCouponMapper;
import com.diybracelet.mall.diy.domain.CordOption;
import com.diybracelet.mall.diy.domain.DiyScheme;
import com.diybracelet.mall.diy.infrastructure.mapper.CordOptionMapper;
import com.diybracelet.mall.diy.infrastructure.mapper.DiySchemeMapper;
import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import com.diybracelet.mall.inventory.application.InventoryReservationService;
import com.diybracelet.mall.order.api.dto.AdminOrderQuery;
import com.diybracelet.mall.order.api.dto.AdminOrderResponse;
import com.diybracelet.mall.order.api.dto.AdminOrderStatistics;
import com.diybracelet.mall.order.api.dto.ShipOrderRequest;
import com.diybracelet.mall.order.api.dto.UpdateOrderAddressRequest;
import com.diybracelet.mall.order.domain.CustomerOrder;
import com.diybracelet.mall.order.domain.FulfillmentTask;
import com.diybracelet.mall.order.domain.OrderItem;
import com.diybracelet.mall.order.domain.OrderOperationLog;
import com.diybracelet.mall.order.domain.OrderStatus;
import com.diybracelet.mall.order.domain.PaymentRecord;
import com.diybracelet.mall.order.infrastructure.mapper.CustomerOrderMapper;
import com.diybracelet.mall.order.infrastructure.mapper.FulfillmentTaskMapper;
import com.diybracelet.mall.order.infrastructure.mapper.OrderItemMapper;
import com.diybracelet.mall.order.infrastructure.mapper.OrderOperationLogMapper;
import com.diybracelet.mall.order.infrastructure.mapper.PaymentRecordMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 后台订单查询、制作、发货与审计服务。 */
@Service
public class AdminOrderService {
    private final CustomerOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final PaymentRecordMapper paymentMapper;
    private final FulfillmentTaskMapper fulfillmentMapper;
    private final OrderOperationLogMapper operationMapper;
    private final DiySchemeMapper schemeMapper;
    private final CordOptionMapper cordOptionMapper;
    private final AssetFileMapper assetFileMapper;
    private final CustomerCouponMapper customerCouponMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final InventoryReservationService inventoryReservationService;

    public AdminOrderService(
            CustomerOrderMapper orderMapper,
            OrderItemMapper itemMapper,
            PaymentRecordMapper paymentMapper,
            FulfillmentTaskMapper fulfillmentMapper,
            OrderOperationLogMapper operationMapper,
            DiySchemeMapper schemeMapper,
            CordOptionMapper cordOptionMapper,
            AssetFileMapper assetFileMapper,
            CustomerCouponMapper customerCouponMapper,
            CouponTemplateMapper couponTemplateMapper,
            InventoryReservationService inventoryReservationService
    ) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.paymentMapper = paymentMapper;
        this.fulfillmentMapper = fulfillmentMapper;
        this.operationMapper = operationMapper;
        this.schemeMapper = schemeMapper;
        this.cordOptionMapper = cordOptionMapper;
        this.assetFileMapper = assetFileMapper;
        this.customerCouponMapper = customerCouponMapper;
        this.couponTemplateMapper = couponTemplateMapper;
        this.inventoryReservationService = inventoryReservationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> list(
            AdminOrderQuery filters,
            int page,
            int size
    ) {
        LambdaQueryWrapper<CustomerOrder> countQuery = query(filters, true);
        long total = orderMapper.selectCount(countQuery);
        long offset = (long) (page - 1) * size;
        LambdaQueryWrapper<CustomerOrder> pageQuery = query(filters, true);
        if (filters.ascending()) {
            pageQuery.orderByAsc(CustomerOrder::getCreatedAt)
                    .orderByAsc(CustomerOrder::getId);
        } else {
            pageQuery.orderByDesc(CustomerOrder::getCreatedAt)
                    .orderByDesc(CustomerOrder::getId);
        }
        pageQuery.last("limit " + size + " offset " + offset);
        List<AdminOrderResponse> items = orderMapper.selectList(pageQuery)
                .stream().map(this::toResponse).toList();
        return new PageResponse<>(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public AdminOrderStatistics statistics(AdminOrderQuery filters) {
        return new AdminOrderStatistics(
                count(filters, null),
                count(filters, OrderStatus.PENDING_PAYMENT),
                count(filters, OrderStatus.IN_PRODUCTION),
                count(filters, OrderStatus.PENDING_SHIPMENT),
                count(filters, OrderStatus.SHIPPED),
                count(filters, OrderStatus.COMPLETED),
                count(filters, OrderStatus.CANCELLED),
                count(filters, OrderStatus.AFTER_SALE),
                count(filters, OrderStatus.REFUNDED)
        );
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse get(Long orderId) {
        CustomerOrder order = orderMapper.selectById(orderId);
        if (order == null) throw notFound();
        return toResponse(order);
    }

    /** 制作中订单完成制作后进入待发货。 */
    @Transactional
    public AdminOrderResponse completeProduction(Long orderId) {
        LockedFulfillment locked = lock(orderId);
        requireState(
                locked, OrderStatus.IN_PRODUCTION, "IN_PRODUCTION",
                "只有制作中订单可以完成制作"
        );
        LocalDateTime completedAt = LocalDateTime.now();
        if (orderMapper.markPendingShipment(orderId) != 1
                || fulfillmentMapper.completeProduction(
                orderId, completedAt
        ) != 1) {
            throw stateConflict("完成制作失败，订单状态已变化");
        }
        addOperation(
                orderId, "PRODUCTION_COMPLETED", "ADMIN", "后台管理员",
                "完成制作，进入待发货", completedAt
        );
        return get(orderId);
    }

    @Transactional
    public AdminOrderResponse ship(Long orderId, ShipOrderRequest request) {
        LockedFulfillment locked = lock(orderId);
        requireState(
                locked, OrderStatus.PENDING_SHIPMENT, "PENDING_SHIPMENT",
                "只有待发货订单可以发货"
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
        addOperation(
                orderId, "ORDER_SHIPPED", "ADMIN", "后台管理员",
                "订单发货：" + request.logisticsCompany().trim()
                        + " / " + request.trackingNo().trim(),
                shippedAt
        );
        return get(orderId);
    }

    /** 后台用于系统自动确认等场景；顾客端也有确认收货入口。 */
    @Transactional
    public AdminOrderResponse complete(Long orderId) {
        LockedFulfillment locked = lock(orderId);
        requireState(
                locked, OrderStatus.SHIPPED, "SHIPPED",
                "只有已发货订单可以完成"
        );
        LocalDateTime completedAt = LocalDateTime.now();
        if (orderMapper.markCompleted(orderId, completedAt) != 1
                || fulfillmentMapper.complete(orderId, completedAt) != 1) {
            throw stateConflict("完成订单失败，订单状态已变化");
        }
        addOperation(
                orderId, "ORDER_COMPLETED", "SYSTEM", "系统",
                "确认订单完成", completedAt
        );
        return get(orderId);
    }

    @Transactional
    public AdminOrderResponse remindPayment(Long orderId) {
        CustomerOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw notFound();
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw stateConflict("只有待付款订单可以提醒付款");
        }
        addOperation(
                orderId, "PAYMENT_REMINDER", "ADMIN", "后台管理员",
                "发送付款提醒", LocalDateTime.now()
        );
        return get(orderId);
    }

    @Transactional
    public AdminOrderResponse cancel(Long orderId) {
        CustomerOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw notFound();
        if (order.getStatus() == OrderStatus.CANCELLED) return get(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw stateConflict("只有待付款订单可以取消");
        }
        List<OrderItem> items = itemMapper.selectByOrderId(orderId);
        inventoryReservationService.release(orderId, quantities(items));
        restoreCoupon(order);
        LocalDateTime cancelledAt = LocalDateTime.now();
        if (orderMapper.markCancelled(orderId, cancelledAt) != 1) {
            throw stateConflict("取消订单失败，订单状态已变化");
        }
        addOperation(
                orderId, "ORDER_CANCELLED", "ADMIN", "后台管理员",
                "取消待付款订单", cancelledAt
        );
        return get(orderId);
    }

    @Transactional
    public AdminOrderResponse updateAddress(
            Long orderId,
            UpdateOrderAddressRequest request
    ) {
        CustomerOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw notFound();
        if (orderMapper.updateAddress(
                orderId,
                request.contactName().trim(),
                request.contactPhone().trim(),
                request.addressDetail().trim(),
                trimToNull(request.orderNote())
        ) != 1) {
            throw stateConflict("当前订单已不能修改收货信息");
        }
        addOperation(
                orderId, "ADDRESS_UPDATED", "ADMIN", "后台管理员",
                "修改收货信息", LocalDateTime.now()
        );
        return get(orderId);
    }

    private long count(AdminOrderQuery filters, OrderStatus status) {
        LambdaQueryWrapper<CustomerOrder> query = query(filters, false);
        if (status != null) query.eq(CustomerOrder::getStatus, status);
        return orderMapper.selectCount(query);
    }

    private LambdaQueryWrapper<CustomerOrder> query(
            AdminOrderQuery filters,
            boolean includeStatus
    ) {
        LambdaQueryWrapper<CustomerOrder> query = new LambdaQueryWrapper<>();
        if (notBlank(filters.orderNo())) {
            query.like(CustomerOrder::getOrderNo, filters.orderNo().trim());
        }
        if (notBlank(filters.contactName())) {
            query.like(CustomerOrder::getContactName, filters.contactName().trim());
        }
        if (notBlank(filters.contactPhone())) {
            query.like(CustomerOrder::getContactPhone, filters.contactPhone().trim());
        }
        if (includeStatus && filters.status() != null) {
            query.eq(CustomerOrder::getStatus, filters.status());
        }
        if (filters.startAt() != null) {
            query.ge(CustomerOrder::getCreatedAt, filters.startAt());
        }
        if (filters.endAt() != null) {
            query.lt(CustomerOrder::getCreatedAt, filters.endAt());
        }
        if (filters.minAmount() != null) {
            query.ge(CustomerOrder::getTotalAmount, filters.minAmount());
        }
        if (filters.maxAmount() != null) {
            query.le(CustomerOrder::getTotalAmount, filters.maxAmount());
        }
        if (notBlank(filters.paymentMethod())) {
            String method = filters.paymentMethod().trim().toUpperCase();
            if ("WECHAT".equals(method)) {
                query.exists("select 1 from payment_record payment "
                        + "where payment.order_id = customer_order.id "
                        + "and payment.channel in ('MOCK', 'WECHAT') "
                        + "and payment.status = 'SUCCESS'");
            }
        }
        return query;
    }

    private LockedFulfillment lock(Long orderId) {
        CustomerOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw notFound();
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
        List<OrderItem> items = itemMapper.selectByOrderId(order.getId());
        FulfillmentTask fulfillment =
                fulfillmentMapper.selectByOrderId(order.getId());
        PaymentRecord payment = paymentMapper.selectByOrderId(order.getId());
        DiyScheme scheme = schemeMapper.selectById(order.getDiySchemeId());
        CordOption cord = scheme == null ? null
                : cordOptionMapper.selectById(scheme.getCordOptionId());
        AssetFile preview = scheme == null || scheme.getPreviewAssetId() == null
                ? null : assetFileMapper.selectById(scheme.getPreviewAssetId());
        List<OrderOperationLog> operations =
                operationMapper.selectByOrderId(order.getId());
        int beadCount = items.stream()
                .mapToInt(OrderItem::getQuantity).sum();
        String couponName = couponName(order.getCouponId());
        return new AdminOrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getDiySchemeId(),
                order.getBeadAmount(),
                order.getCraftFee(),
                order.getCordFee(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                couponName,
                order.getContactName(),
                order.getContactPhone(),
                order.getAddressDetail(),
                order.getOrderNote(),
                new AdminOrderResponse.Product(
                        scheme == null ? "专属 DIY 手串" : scheme.getName(),
                        "#" + order.getDiySchemeId(),
                        scheme == null ? null : scheme.getWristCircumferenceMm(),
                        scheme == null ? null : scheme.getFitType().name(),
                        cord == null ? "弹力绳" : cord.getName(),
                        beadCount,
                        Math.max(15, beadCount + 3),
                        preview != null && "ACTIVE".equals(preview.getStatus())
                                ? preview.getPublicUrl() : null
                ),
                items.stream().map(item -> new AdminOrderResponse.Item(
                        item.getBeadSkuId(), item.getSkuCode(), item.getName(),
                        item.getQuantity(), item.getUnitPrice(),
                        item.getTotalPrice()
                )).toList(),
                payment == null ? null : new AdminOrderResponse.Payment(
                        payment.getChannel(), payment.getStatus(),
                        payment.getAmount(), payment.getPaidAt()
                ),
                toFulfillment(fulfillment),
                operations.stream().map(operation ->
                        new AdminOrderResponse.Operation(
                                operation.getId(), operation.getActionCode(),
                                operation.getOperatorType(),
                                operation.getOperatorName(),
                                operation.getContent(), operation.getOccurredAt()
                        )).toList(),
                order.getPaidAt(),
                order.getCancelledAt(),
                order.getShippedAt(),
                order.getCompletedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private AdminOrderResponse.Fulfillment toFulfillment(
            FulfillmentTask task
    ) {
        if (task == null) return null;
        return new AdminOrderResponse.Fulfillment(
                task.getId(), task.getStatus(), task.getLogisticsCompany(),
                task.getTrackingNo(), task.getProductionStartedAt(),
                task.getProductionCompletedAt(), task.getShippedAt(),
                task.getCompletedAt()
        );
    }

    private String couponName(Long customerCouponId) {
        if (customerCouponId == null) return null;
        CustomerCoupon coupon = customerCouponMapper.selectById(customerCouponId);
        if (coupon == null) return "优惠券";
        CouponTemplate template =
                couponTemplateMapper.selectById(coupon.getCouponTemplateId());
        return template == null ? "优惠券" : template.getName();
    }

    private Map<Long, Integer> quantities(List<OrderItem> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItem item : items) {
            quantities.put(item.getBeadSkuId(), item.getQuantity());
        }
        return quantities;
    }

    private void restoreCoupon(CustomerOrder order) {
        if (order.getCouponId() == null) return;
        CustomerCoupon coupon = customerCouponMapper.selectById(order.getCouponId());
        if (coupon == null || !"USED".equals(coupon.getStatus())) return;
        coupon.setStatus("UNUSED");
        coupon.setUsedAt(null);
        customerCouponMapper.updateById(coupon);
    }

    private void addOperation(
            Long orderId,
            String actionCode,
            String operatorType,
            String operatorName,
            String content,
            LocalDateTime occurredAt
    ) {
        OrderOperationLog operation = new OrderOperationLog();
        operation.setOrderId(orderId);
        operation.setActionCode(actionCode);
        operation.setOperatorType(operatorType);
        operation.setOperatorName(operatorName);
        operation.setContent(content);
        operation.setOccurredAt(occurredAt);
        operationMapper.insert(operation);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.COMMON_NOT_FOUND, "订单不存在");
    }

    private BusinessException stateConflict(String message) {
        return new BusinessException(ErrorCode.COMMON_CONFLICT, message);
    }

    private record LockedFulfillment(
            CustomerOrder order,
            FulfillmentTask fulfillment
    ) { }
}
