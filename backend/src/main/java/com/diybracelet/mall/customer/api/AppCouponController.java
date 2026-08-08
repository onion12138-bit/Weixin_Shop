package com.diybracelet.mall.customer.api;

import com.diybracelet.mall.customer.api.dto.CustomerDtos.CustomerCouponView;
import com.diybracelet.mall.customer.application.CouponService;
import com.diybracelet.mall.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/app/coupons")
public class AppCouponController {
    private final CouponService couponService;

    public AppCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public ApiResponse<List<CustomerCouponView>> listMine() {
        return ApiResponse.success(
                couponService.listMine(),
                UUID.randomUUID().toString()
        );
    }
}
