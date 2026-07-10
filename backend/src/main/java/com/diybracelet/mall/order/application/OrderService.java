package com.diybracelet.mall.order.application;

import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
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
import com.diybracelet.mall.order.domain.OrderItem;
import com.diybracelet.mall.order.domain.OrderStatus;
import com.diybracelet.mall.order.infrastructure.mapper.CustomerOrderMapper;
import com.diybracelet.mall.order.infrastructure.mapper.OrderItemMapper;
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
    private final InventoryReservationService inventoryReservationService;
    private final ObjectMapper objectMapper;

    public OrderService(
            CustomerIdentityResolver identityResolver,
            DiySchemeMapper schemeMapper,
            DiyPricingService pricingService,
            BeadSkuMapper beadSkuMapper,
            CustomerOrderMapper orderMapper,
            OrderItemMapper itemMapper,
            InventoryReservationService inventoryReservationService,
            ObjectMapper objectMapper
    ) {
        this.identityResolver = identityResolver;
        this.schemeMapper = schemeMapper;
        this.pricingService = pricingService;
        this.beadSkuMapper = beadSkuMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
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

        Map<Long, Integer> quantities = quantitiesByFirstAppearance(sequence);
        Map<Long, BeadSku> beadsById = loadSellableBeads(quantities);
        CustomerOrder order = buildOrder(
                customerId,
                scheme,
                request,
                sequence,
                quantities,
                evaluation
        );
        orderMapper.insert(order);
        List<OrderItem> items = buildItems(order.getId(), quantities, beadsById);
        for (OrderItem item : items) {
            itemMapper.insert(item);
        }
        inventoryReservationService.reserve(order.getId(), quantities);
        CustomerOrder saved = orderMapper.selectById(order.getId());
        return toResponse(saved, itemMapper.selectByOrderId(order.getId()));
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
            DiyEvaluation evaluation
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
        order.setTotalAmount(evaluation.totalAmount());
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
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getDiySchemeId(),
                order.getBeadAmount(),
                order.getCraftFee(),
                order.getCordFee(),
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
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private Map<Long, Integer> quantitiesByFirstAppearance(List<Long> sequence) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (Long skuId : sequence) {
            quantities.put(skuId, quantities.getOrDefault(skuId, 0) + 1);
        }
        return quantities;
    }

    private String generateOrderNo() {
        return "ORD"
                + LocalDateTime.now().format(ORDER_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

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
