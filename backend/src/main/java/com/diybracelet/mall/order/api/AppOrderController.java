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
}
