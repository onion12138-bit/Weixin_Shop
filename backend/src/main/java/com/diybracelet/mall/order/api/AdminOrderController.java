package com.diybracelet.mall.order.api;

import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.order.api.dto.AdminOrderQuery;
import com.diybracelet.mall.order.api.dto.AdminOrderResponse;
import com.diybracelet.mall.order.api.dto.AdminOrderStatistics;
import com.diybracelet.mall.order.api.dto.ShipOrderRequest;
import com.diybracelet.mall.order.api.dto.UpdateOrderAddressRequest;
import com.diybracelet.mall.order.application.AdminOrderService;
import com.diybracelet.mall.order.domain.OrderStatus;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** 管理后台订单列表、详情和履约动作接口。 */
@Validated
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminOrderResponse>> list(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String contactPhone,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return success(adminOrderService.list(
                query(
                        orderNo, contactName, contactPhone, status,
                        startDate, endDate, minAmount, maxAmount,
                        paymentMethod, sortDirection
                ),
                page,
                size
        ));
    }

    @GetMapping("/statistics")
    public ApiResponse<AdminOrderStatistics> statistics(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String contactPhone,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String paymentMethod
    ) {
        return success(adminOrderService.statistics(query(
                orderNo, contactName, contactPhone, null,
                startDate, endDate, minAmount, maxAmount,
                paymentMethod, "desc"
        )));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<AdminOrderResponse> get(
            @PathVariable @Min(1) Long orderId
    ) {
        return success(adminOrderService.get(orderId));
    }

    @PostMapping("/{orderId}/complete-production")
    public ApiResponse<AdminOrderResponse> completeProduction(
            @PathVariable @Min(1) Long orderId
    ) {
        return success(adminOrderService.completeProduction(orderId));
    }

    @PostMapping("/{orderId}/ship")
    public ApiResponse<AdminOrderResponse> ship(
            @PathVariable @Min(1) Long orderId,
            @Valid @RequestBody ShipOrderRequest request
    ) {
        return success(adminOrderService.ship(orderId, request));
    }

    @PostMapping("/{orderId}/complete")
    public ApiResponse<AdminOrderResponse> complete(
            @PathVariable @Min(1) Long orderId
    ) {
        return success(adminOrderService.complete(orderId));
    }

    @PostMapping("/{orderId}/remind-payment")
    public ApiResponse<AdminOrderResponse> remindPayment(
            @PathVariable @Min(1) Long orderId
    ) {
        return success(adminOrderService.remindPayment(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<AdminOrderResponse> cancel(
            @PathVariable @Min(1) Long orderId
    ) {
        return success(adminOrderService.cancel(orderId));
    }

    @PatchMapping("/{orderId}/address")
    public ApiResponse<AdminOrderResponse> updateAddress(
            @PathVariable @Min(1) Long orderId,
            @Valid @RequestBody UpdateOrderAddressRequest request
    ) {
        return success(adminOrderService.updateAddress(orderId, request));
    }

    private AdminOrderQuery query(
            String orderNo,
            String contactName,
            String contactPhone,
            OrderStatus status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String paymentMethod,
            String sortDirection
    ) {
        return new AdminOrderQuery(
                orderNo,
                contactName,
                contactPhone,
                status,
                startDate == null ? null : startDate.atStartOfDay(),
                endDate == null ? null : endDate.plusDays(1).atStartOfDay(),
                minAmount,
                maxAmount,
                paymentMethod,
                "asc".equalsIgnoreCase(sortDirection)
        );
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }
}
