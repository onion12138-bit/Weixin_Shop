package com.diybracelet.mall.commerce.api;

import com.diybracelet.mall.commerce.api.dto.CommerceRuleView;
import com.diybracelet.mall.commerce.api.dto.SaveCommerceRuleRequest;
import com.diybracelet.mall.commerce.application.CommerceRuleService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 后台交易规则配置接口。 */
@RestController
@RequestMapping("/api/admin/commerce/config")
public class AdminCommerceRuleController {

    private final CommerceRuleService service;

    public AdminCommerceRuleController(CommerceRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<CommerceRuleView> get() {
        return ApiResponse.success(service.get(), UUID.randomUUID().toString());
    }

    @PutMapping
    public ApiResponse<CommerceRuleView> save(
            @Valid @RequestBody SaveCommerceRuleRequest request
    ) {
        return ApiResponse.success(service.save(request), UUID.randomUUID().toString());
    }
}
