package com.diybracelet.mall.diy.api;

import com.diybracelet.mall.diy.api.dto.DiyQuoteRequest;
import com.diybracelet.mall.diy.api.dto.DiyQuoteResponse;
import com.diybracelet.mall.diy.application.DiyPricingService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 小程序 DIY 权威报价接口。
 *
 * <p>请求必须携带顾客 JWT。接口始终按服务端当前 SKU、规则和绳材计算结果；
 * 不可信任小程序端展示的预估金额。</p>
 */
@RestController
@RequestMapping("/api/app/diy")
public class AppDiyPricingController {

    private final DiyPricingService diyPricingService;

    public AppDiyPricingController(DiyPricingService diyPricingService) {
        this.diyPricingService = diyPricingService;
    }

    /**
     * 对当前设计进行权威报价。
     *
     * @param request 报价请求
     * @return 报价响应；不可报价时包含可修复问题
     */
    @PostMapping("/quotes")
    public ApiResponse<DiyQuoteResponse> quote(
            @Valid @RequestBody DiyQuoteRequest request
    ) {
        return ApiResponse.success(
                DiyQuoteResponse.from(
                        diyPricingService.evaluate(request.toCommand())
                ),
                UUID.randomUUID().toString()
        );
    }
}
