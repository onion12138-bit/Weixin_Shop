package com.diybracelet.mall.commerce.api;

import com.diybracelet.mall.commerce.api.dto.CommerceRuleView;
import com.diybracelet.mall.commerce.application.CommerceRuleService;
import com.diybracelet.mall.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 小程序读取交易门槛和运费规则。 */
@RestController
@RequestMapping("/api/app/commerce/config")
public class AppCommerceRuleController {

    private final CommerceRuleService service;

    public AppCommerceRuleController(CommerceRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<CommerceRuleView> get() {
        return ApiResponse.success(service.get(), UUID.randomUUID().toString());
    }
}
