package com.diybracelet.mall.order.api;

import com.diybracelet.mall.order.api.dto.CreateOrderRequest;
import com.diybracelet.mall.order.api.dto.OrderResponse;
import com.diybracelet.mall.order.application.OrderService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 小程序订单接口。
 */
@Validated
@RestController
@RequestMapping("/api/app/orders")
public class AppOrderController {

    private final OrderService orderService;

    public AppOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 基于当前顾客拥有的 DIY 方案创建待支付订单。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        orderService.create(request),
                        UUID.randomUUID().toString()
                ));
    }

    /**
     * 本地模拟支付。重复请求已支付订单时返回相同业务结果。
     */
    @PostMapping("/{orderId}/mock-pay")
    public ApiResponse<OrderResponse> mockPay(
            @PathVariable Long orderId
    ) {
        return ApiResponse.success(
                orderService.mockPay(orderId),
                UUID.randomUUID().toString()
        );
    }

    /**
     * 取消待支付订单并释放库存预占。
     */
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancel(
            @PathVariable Long orderId
    ) {
        return ApiResponse.success(
                orderService.cancel(orderId),
                UUID.randomUUID().toString()
        );
    }
}
