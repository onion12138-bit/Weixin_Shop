package com.diybracelet.mall.order.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.commerce.application.CommerceRuleService;
import com.diybracelet.mall.customer.domain.CouponTemplate;
import com.diybracelet.mall.customer.domain.CustomerCoupon;
import com.diybracelet.mall.customer.infrastructure.CouponTemplateMapper;
import com.diybracelet.mall.customer.infrastructure.CustomerCouponMapper;
import com.diybracelet.mall.diy.domain.DiyDesignCommand;
import com.diybracelet.mall.diy.domain.DiyEvaluation;
import com.diybracelet.mall.diy.domain.DiyScheme;
import com.diybracelet.mall.diy.infrastructure.mapper.DiySchemeMapper;
import com.diybracelet.mall.diy.application.DiyPricingService;
import com.diybracelet.mall.identity.domain.CustomerIdentityResolver;
import com.diybracelet.mall.inventory.application.InventoryReservationService;
import com.diybracelet.mall.order.api.dto.CreateOrderRequest;
import com.diybracelet.mall.order.api.dto.OrderResponse;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 小程序订单应用服务。
 *
 * <p>订单创建是库存、DIY 方案和价格的交汇点。服务会重新执行权威报价并在同一事务内
 * 生成订单、明细和库存预占，任何一步失败都会回滚，避免出现“有订单无库存”或
 * “扣库存但无订单”的中间状态。</p>
 */
@Service
public class OrderService {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE =
            new TypeReference<>() {
            };
    private static final DateTimeFormatter ORDER_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CustomerIdentityResolver identityResolver;
    private final DiySchemeMapper schemeMapper;
    private final DiyPricingService pricingService;
    private final BeadSkuMapper beadSkuMapper;
    private final CustomerOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final PaymentRecordMapper paymentMapper;
    private final FulfillmentTaskMapper fulfillmentMapper;
    private final OrderOperationLogMapper operationMapper;
    private final CustomerCouponMapper customerCouponMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final CommerceRuleService commerceRuleService;
    private final InventoryReservationService inventoryReservationService;
    private final ObjectMapper objectMapper;

    public OrderService(
            CustomerIdentityResolver identityResolver,
            DiySchemeMapper schemeMapper,
            DiyPricingService pricingService,
            BeadSkuMapper beadSkuMapper,
            CustomerOrderMapper orderMapper,
            OrderItemMapper itemMapper,
            PaymentRecordMapper paymentMapper,
            FulfillmentTaskMapper fulfillmentMapper,
            OrderOperationLogMapper operationMapper,
            CustomerCouponMapper customerCouponMapper,
            CouponTemplateMapper couponTemplateMapper,
            CommerceRuleService commerceRuleService,
            InventoryReservationService inventoryReservationService,
            ObjectMapper objectMapper
    ) {
        this.identityResolver = identityResolver;
        this.schemeMapper = schemeMapper;
        this.pricingService = pricingService;
        this.beadSkuMapper = beadSkuMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.paymentMapper = paymentMapper;
        this.fulfillmentMapper = fulfillmentMapper;
        this.operationMapper = operationMapper;
        this.customerCouponMapper = customerCouponMapper;
        this.couponTemplateMapper = couponTemplateMapper;
        this.commerceRuleService = commerceRuleService;
        this.inventoryReservationService = inventoryReservationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 基于当前顾客拥有的 DIY 方案创建待支付订单并预占库存。
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        DiyScheme scheme = requireOwnedScheme(request.schemeId(), customerId);
        List<Long> sequence = parseSequence(scheme.getSequenceJson());
        DiyEvaluation evaluation = pricingService.evaluate(new DiyDesignCommand(
                scheme.getWristCircumferenceMm(),
                scheme.getFitType(),
                scheme.getCordOptionId(),
                sequence
        ));
        if (!evaluation.quotable()) {
            throw new BusinessException(
                    ErrorCode.DIY_DESIGN_NOT_QUOTABLE,
                    "当前方案不可报价，不能下单"
            );
        }
        CommerceRuleService.CheckoutRule checkoutRule =
                commerceRuleService.checkoutRule();
        if (evaluation.totalAmount().compareTo(
                checkoutRule.minimumOrderAmount()
        ) < 0) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "订单商品金额需满 "
                            + checkoutRule.minimumOrderAmount()
                                    .stripTrailingZeros().toPlainString()
                            + " 元才能下单"
            );
        }

        Map<Long, Integer> quantities = quantitiesByFirstAppearance(sequence);
        Map<Long, BeadSku> beadsById = loadSellableBeads(quantities);
        CouponSelection coupon = selectCoupon(
                request.couponId(),
                customerId,
                evaluation.totalAmount()
        );
        CustomerOrder order = buildOrder(
                customerId,
                scheme,
                request,
                sequence,
                quantities,
                evaluation,
                coupon,
                checkoutRule
        );
        orderMapper.insert(order);
        addOperation(
                order.getId(), "ORDER_CREATED", "USER", "用户", "提交订单",
                LocalDateTime.now()
        );
        List<OrderItem> items = buildItems(order.getId(), quantities, beadsById);
        for (OrderItem item : items) {
            itemMapper.insert(item);
        }
        inventoryReservationService.reserve(order.getId(), quantities);
        markCouponUsed(coupon);
        if (schemeMapper.softDeleteOwned(scheme.getId(), customerId) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "方案状态已发生变化，请刷新后重试"
            );
        }
        CustomerOrder saved = orderMapper.selectById(order.getId());
        return toResponse(saved, itemMapper.selectByOrderId(order.getId()));
    }

    /**
     * 查询当前顾客的订单列表，最新创建的订单排在最前。
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> listOwned() {
        Long customerId = identityResolver.requireCustomerIdentityId();
        return orderMapper.selectOwnedOrders(customerId).stream()
                .map(order -> toResponse(
                        order,
                        itemMapper.selectByOrderId(order.getId())
                ))
                .toList();
    }

    /**
     * 查询当前顾客拥有的订单详情。
     */
    @Transactional(readOnly = true)
    public OrderResponse getOwned(Long orderId) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        CustomerOrder order = orderMapper.selectOwnedById(
                orderId,
                customerId
        );
        if (order == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "订单不存在"
            );
        }
        return toResponse(order, itemMapper.selectByOrderId(orderId));
    }

    /**
     * 模拟完成订单支付。
     *
     * <p>先锁定当前顾客的订单行。已支付订单直接返回当前结果，避免重复扣库存、
     * 重复写支付流水和重复创建制作任务。</p>
     */
    @Transactional
    public OrderResponse mockPay(Long orderId) {
        CustomerOrder order = requireOwnedOrderForUpdate(orderId);
        List<OrderItem> items = itemMapper.selectByOrderId(orderId);
        if (order.getPaidAt() != null
                && order.getStatus() != OrderStatus.CANCELLED) {
            return toResponse(order, items);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "当前订单状态不能支付"
            );
        }

        LocalDateTime paidAt = LocalDateTime.now();
        inventoryReservationService.confirmSale(
                orderId,
                itemQuantities(items)
        );
        createPaymentRecord(order, paidAt);
        createFulfillmentTask(orderId, items, paidAt);
        if (orderMapper.markPaidAndStartProduction(orderId, paidAt) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "订单支付状态更新失败"
            );
        }
        addOperation(
                orderId, "PAYMENT_SUCCEEDED", "SYSTEM", "系统",
                "微信支付成功", paidAt
        );
        addOperation(
                orderId, "BOM_GENERATED", "SYSTEM", "系统",
                "生成制作 BOM", paidAt
        );
        return currentResponse(orderId);
    }

    /**
     * 取消待支付订单并释放全部预占库存。
     */
    @Transactional
    public OrderResponse cancel(Long orderId) {
        CustomerOrder order = requireOwnedOrderForUpdate(orderId);
        List<OrderItem> items = itemMapper.selectByOrderId(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order, items);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "当前订单状态不能取消"
            );
        }

        inventoryReservationService.release(orderId, itemQuantities(items));
        restoreCoupon(order);
        LocalDateTime cancelledAt = LocalDateTime.now();
        if (orderMapper.markCancelled(orderId, cancelledAt) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "订单取消状态更新失败"
            );
        }
        addOperation(
                orderId, "ORDER_CANCELLED", "USER", "用户",
                "取消订单", cancelledAt
        );
        return currentResponse(orderId);
    }

    /** 顾客确认收货后，后台与小程序同步进入已完成。 */
    @Transactional
    public OrderResponse confirmReceipt(Long orderId) {
        CustomerOrder order = requireOwnedOrderForUpdate(orderId);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            return currentResponse(orderId);
        }
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "只有待收货订单可以确认收货"
            );
        }
        FulfillmentTask fulfillment =
                fulfillmentMapper.selectByOrderIdForUpdate(orderId);
        if (fulfillment == null || !"SHIPPED".equals(fulfillment.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "订单履约状态已发生变化"
            );
        }
        LocalDateTime completedAt = LocalDateTime.now();
        if (orderMapper.markCompleted(orderId, completedAt) != 1
                || fulfillmentMapper.complete(orderId, completedAt) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "确认收货失败，订单状态已变化"
            );
        }
        addOperation(
                orderId, "RECEIPT_CONFIRMED", "USER", "用户",
                "确认收货", completedAt
        );
        return currentResponse(orderId);
    }

    private CustomerOrder requireOwnedOrderForUpdate(Long orderId) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        CustomerOrder order = orderMapper.selectOwnedByIdForUpdate(
                orderId,
                customerId
        );
        if (order == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "订单不存在"
            );
        }
        return order;
    }

    private Map<Long, Integer> itemQuantities(List<OrderItem> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItem item : items) {
            quantities.put(item.getBeadSkuId(), item.getQuantity());
        }
        return quantities;
    }

    private void createPaymentRecord(
            CustomerOrder order,
            LocalDateTime paidAt
    ) {
        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(order.getId());
        payment.setPaymentNo("MOCK"
                + paidAt.format(ORDER_TIME)
                + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8));
        payment.setChannel("MOCK");
        payment.setStatus("SUCCESS");
        payment.setAmount(order.getTotalAmount());
        payment.setPaidAt(paidAt);
        payment.setRawResponseJson(writeJson(Map.of(
                "provider", "LOCAL_MOCK",
                "result", "SUCCESS"
        )));
        paymentMapper.insert(payment);
    }

    private void createFulfillmentTask(
            Long orderId,
            List<OrderItem> items,
            LocalDateTime productionStartedAt
    ) {
        List<Map<String, Object>> bom = items.stream()
                .map(item -> Map.<String, Object>of(
                        "skuId", item.getBeadSkuId(),
                        "skuCode", item.getSkuCode(),
                        "name", item.getName(),
                        "quantity", item.getQuantity()
                ))
                .toList();
        FulfillmentTask task = new FulfillmentTask();
        task.setOrderId(orderId);
        task.setStatus("IN_PRODUCTION");
        task.setProductionStartedAt(productionStartedAt);
        task.setBomJson(writeJson(bom));
        fulfillmentMapper.insert(task);
    }

    private OrderResponse currentResponse(Long orderId) {
        return toResponse(
                orderMapper.selectById(orderId),
                itemMapper.selectByOrderId(orderId)
        );
    }

    private DiyScheme requireOwnedScheme(Long schemeId, Long customerId) {
        DiyScheme scheme = schemeMapper.selectOwnedById(schemeId, customerId);
        if (scheme == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "方案不存在"
            );
        }
        return scheme;
    }

    private Map<Long, BeadSku> loadSellableBeads(Map<Long, Integer> quantities) {
        List<Long> skuIds = List.copyOf(quantities.keySet());
        Map<Long, BeadSku> beadsById = beadSkuMapper.selectSellableForDiy(skuIds)
                .stream()
                .collect(Collectors.toMap(BeadSku::getId, Function.identity()));
        if (beadsById.size() != quantities.size()) {
            throw new BusinessException(
                    ErrorCode.DIY_DESIGN_NOT_QUOTABLE,
                    "方案中存在不可售珠子，不能下单"
            );
        }
        return beadsById;
    }

    private CustomerOrder buildOrder(
            Long customerId,
            DiyScheme scheme,
            CreateOrderRequest request,
            List<Long> sequence,
            Map<Long, Integer> quantities,
            DiyEvaluation evaluation,
            CouponSelection coupon,
            CommerceRuleService.CheckoutRule checkoutRule
    ) {
        CustomerOrder order = new CustomerOrder();
        order.setOrderNo(generateOrderNo());
        order.setCustomerIdentityId(customerId);
        order.setDiySchemeId(scheme.getId());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setContactName(request.contactName().trim());
        order.setContactPhone(request.contactPhone().trim());
        order.setAddressDetail(request.addressDetail().trim());
        order.setSchemeSnapshotJson(writeJson(Map.of(
                "schemeId", scheme.getId(),
                "schemeName", scheme.getName(),
                "sequence", sequence,
                "skuQuantities", quantities,
                "quoteFingerprint", evaluation.quoteFingerprint()
        )));
        order.setBeadAmount(evaluation.beadAmount());
        order.setCraftFee(evaluation.craftFee());
        order.setCordFee(evaluation.cordFee());
        order.setCouponId(coupon.customerCoupon() == null
                ? null : coupon.customerCoupon().getId());
        order.setDiscountAmount(coupon.discountAmount());
        BigDecimal discountedProductAmount = evaluation.totalAmount()
                .subtract(coupon.discountAmount());
        order.setShippingFee(checkoutRule.shippingFee(discountedProductAmount));
        order.setTotalAmount(discountedProductAmount.add(order.getShippingFee()));
        order.setVersion(0L);
        return order;
    }

    private List<OrderItem> buildItems(
            Long orderId,
            Map<Long, Integer> quantities,
            Map<Long, BeadSku> beadsById
    ) {
        int[] sortOrder = {10};
        return quantities.entrySet().stream().map(entry -> {
            BeadSku bead = beadsById.get(entry.getKey());
            OrderItem item = new OrderItem();
            item.setOrderId(orderId);
            item.setBeadSkuId(bead.getId());
            item.setSkuCode(bead.getSkuCode());
            item.setName(bead.getName());
            item.setQuantity(entry.getValue());
            item.setUnitPrice(bead.getSalePrice());
            item.setTotalPrice(bead.getSalePrice()
                    .multiply(BigDecimal.valueOf(entry.getValue())));
            item.setSortOrder(sortOrder[0]);
            sortOrder[0] += 10;
            return item;
        }).toList();
    }

    private OrderResponse toResponse(
            CustomerOrder order,
            List<OrderItem> items
    ) {
        FulfillmentTask fulfillment =
                fulfillmentMapper.selectByOrderId(order.getId());
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getDiySchemeId(),
                order.getBeadAmount(),
                order.getCraftFee(),
                order.getCordFee(),
                order.getCouponId(),
                order.getDiscountAmount(),
                order.getShippingFee(),
                order.getTotalAmount(),
                order.getContactName(),
                order.getContactPhone(),
                order.getAddressDetail(),
                items.stream().map(item -> new OrderResponse.Item(
                        item.getBeadSkuId(),
                        item.getSkuCode(),
                        item.getName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalPrice()
                )).toList(),
                fulfillment == null ? null : fulfillment.getLogisticsCompany(),
                fulfillment == null ? null : fulfillment.getTrackingNo(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getCompletedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
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

    private Map<Long, Integer> quantitiesByFirstAppearance(List<Long> sequence) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (Long skuId : sequence) {
            quantities.put(skuId, quantities.getOrDefault(skuId, 0) + 1);
        }
        return quantities;
    }

    private static String generateOrderNo() {
        return "ORD"
                + LocalDateTime.now().format(ORDER_TIME)
                + String.format("%08d", Math.floorMod(
                        UUID.randomUUID().getMostSignificantBits(),
                        100_000_000L
                ));
    }

    private CouponSelection selectCoupon(
            Long couponId,
            Long customerId,
            BigDecimal orderAmount
    ) {
        if (couponId == null) {
            return new CouponSelection(null, BigDecimal.ZERO);
        }
        CustomerCoupon customerCoupon = customerCouponMapper.selectOne(
                new LambdaQueryWrapper<CustomerCoupon>()
                        .eq(CustomerCoupon::getId, couponId)
                        .eq(CustomerCoupon::getCustomerIdentityId, customerId)
                        .last("for update")
        );
        if (customerCoupon == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "优惠券不存在"
            );
        }
        if (!"UNUSED".equals(customerCoupon.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "优惠券已使用或已失效"
            );
        }
        if (!customerCoupon.getExpiresAt().isAfter(LocalDateTime.now())) {
            customerCoupon.setStatus("EXPIRED");
            customerCouponMapper.updateById(customerCoupon);
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "优惠券已过期"
            );
        }
        CouponTemplate template = couponTemplateMapper.selectById(
                customerCoupon.getCouponTemplateId()
        );
        if (template == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "优惠券模板不存在"
            );
        }
        if (orderAmount.compareTo(template.getMinimumSpend()) < 0) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "订单金额未达到优惠券使用门槛"
            );
        }
        return new CouponSelection(
                customerCoupon,
                template.getDiscountAmount().min(orderAmount)
        );
    }

    private void markCouponUsed(CouponSelection selection) {
        CustomerCoupon coupon = selection.customerCoupon();
        if (coupon == null) return;
        coupon.setStatus("USED");
        coupon.setUsedAt(LocalDateTime.now());
        if (customerCouponMapper.updateById(coupon) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "优惠券核销失败，请刷新后重试"
            );
        }
    }

    private void restoreCoupon(CustomerOrder order) {
        if (order.getCouponId() == null) return;
        CustomerCoupon coupon = customerCouponMapper.selectById(
                order.getCouponId()
        );
        if (coupon == null || !"USED".equals(coupon.getStatus())) return;
        coupon.setStatus("UNUSED");
        coupon.setUsedAt(null);
        if (customerCouponMapper.updateById(coupon) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "优惠券退回失败，请刷新后重试"
            );
        }
    }

    private record CouponSelection(
            CustomerCoupon customerCoupon,
            BigDecimal discountAmount
    ) { }

    private List<Long> parseSequence(String json) {
        try {
            return List.copyOf(objectMapper.readValue(json, LONG_LIST_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("订单方案顺序数组不是有效 JSON", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("订单快照 JSON 序列化失败", exception);
        }
    }
}
