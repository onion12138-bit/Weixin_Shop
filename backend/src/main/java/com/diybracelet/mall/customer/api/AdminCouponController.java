package com.diybracelet.mall.customer.api;

import com.diybracelet.mall.customer.api.dto.CustomerDtos.BatchGrantCouponRequest;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.BatchGrantCouponResult;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.CouponTemplateView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.CustomerCouponView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.GrantCouponRequest;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.SaveCouponTemplateRequest;
import com.diybracelet.mall.customer.application.CouponService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {
    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public ApiResponse<List<CouponTemplateView>> listTemplates() {
        return success(couponService.listTemplates());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CouponTemplateView>> createTemplate(
            @Valid @RequestBody SaveCouponTemplateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(couponService.createTemplate(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<CouponTemplateView> updateTemplate(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody SaveCouponTemplateRequest request
    ) {
        return success(couponService.updateTemplate(id, request));
    }

    @PostMapping("/{id}/grants")
    public ResponseEntity<ApiResponse<CustomerCouponView>> grant(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody GrantCouponRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(couponService.grant(id, request)));
    }

    @PostMapping("/{id}/grants/batch")
    public ResponseEntity<ApiResponse<BatchGrantCouponResult>> grantBatch(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody BatchGrantCouponRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(couponService.grantBatch(id, request)));
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }
}
