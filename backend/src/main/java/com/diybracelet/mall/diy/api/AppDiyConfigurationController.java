package com.diybracelet.mall.diy.api;

import com.diybracelet.mall.diy.api.dto.DiyConfigurationView;
import com.diybracelet.mall.diy.application.DiyConfigurationService;
import com.diybracelet.mall.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 小程序 DIY 配置接口。
 *
 * <p>该接口要求顾客 JWT，返回当前启用的尺寸、工艺费和绳材配置。小程序使用它进行实时
 * 预估和编辑约束展示，最终权威报价仍由后端重新读取配置计算。</p>
 */
@RestController
@RequestMapping("/api/app/diy")
public class AppDiyConfigurationController {

    private final DiyConfigurationService diyConfigurationService;

    public AppDiyConfigurationController(
            DiyConfigurationService diyConfigurationService
    ) {
        this.diyConfigurationService = diyConfigurationService;
    }

    /**
     * 获取小程序端 DIY 配置。
     *
     * @return 启用配置视图
     */
    @GetMapping("/config")
    public ApiResponse<DiyConfigurationView> appConfiguration() {
        return ApiResponse.success(
                diyConfigurationService.getPublicConfiguration(),
                UUID.randomUUID().toString()
        );
    }
}
