package com.diybracelet.mall.order.api;

import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.order.api.dto.AdminOrderResponse;
import com.diybracelet.mall.order.api.dto.ShipOrderRequest;
import com.diybracelet.mall.order.application.AdminOrderService;
import com.diybracelet.mall.order.domain.OrderStatus;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 管理后台订单查询和履约操作接口。
 *
 * <p>安全配置统一要求管理员角色。履约采用明确动作接口，避免前端直接提交任意目标状态。</p>
 */
@Validated
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    /**
     * 分页查询订单，可按订单状态筛选。
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminOrderResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page 必须大于等于 1")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size 必须大于等于 1")
            @Max(value = 100, message = "size 不能超过 100")
            int size
    ) {
        return success(adminOrderService.list(status, page, size));
    }

    /**
     * 查询订单与 BOM、履约详情。
     */
    @GetMapping("/{orderId}")
    public ApiResponse<AdminOrderResponse> get(
            @PathVariable @Min(1) Long orderId
    ) {
        return success(adminOrderService.get(orderId));
    }

    /**
     * 已支付订单开始制作。
     */
    @PostMapping("/{orderId}/start-production")
    public ApiResponse<AdminOrderResponse> startProduction(
            @PathVariable @Min(1) Long orderId
    ) {
        return success(adminOrderService.startProduction(orderId));
    }

    /**
     * 制作中订单发货并保存模拟物流信息。
     */
    @PostMapping("/{orderId}/ship")
    public ApiResponse<AdminOrderResponse> ship(
            @PathVariable @Min(1) Long orderId,
            @Valid @RequestBody ShipOrderRequest request
    ) {
        return success(adminOrderService.ship(orderId, request));
    }

    /**
     * 已发货订单完成履约。
     */
    @PostMapping("/{orderId}/complete")
    public ApiResponse<AdminOrderResponse> complete(
            @PathVariable @Min(1) Long orderId
    ) {
        return success(adminOrderService.complete(orderId));
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }
}
