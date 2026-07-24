package com.diybracelet.mall.diy.api;

import com.diybracelet.mall.diy.api.dto.AdminDiyConfigurationView;
import com.diybracelet.mall.diy.api.dto.DiyConfigurationView;
import com.diybracelet.mall.diy.api.dto.SaveCordOptionRequest;
import com.diybracelet.mall.diy.api.dto.SaveCraftFeeRulesRequest;
import com.diybracelet.mall.diy.api.dto.SaveDiyRuleConfigRequest;
import com.diybracelet.mall.diy.api.dto.SaveDiyVisualConfigRequest;
import com.diybracelet.mall.diy.application.DiyConfigurationService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * 管理后台 DIY 配置接口。
 *
 * <p>后台可调整尺寸规则、工艺费区间和绳材。所有写操作都在服务层进行完整校验，
 * Controller 只保留清晰的 HTTP 契约。</p>
 */
@RestController
@RequestMapping("/api/admin/diy")
public class AdminDiyConfigurationController {

    private final DiyConfigurationService diyConfigurationService;

    public AdminDiyConfigurationController(
            DiyConfigurationService diyConfigurationService
    ) {
        this.diyConfigurationService = diyConfigurationService;
    }

    /**
     * 获取管理端完整配置。
     *
     * @return 管理端配置视图
     */
    @GetMapping("/config")
    public ApiResponse<AdminDiyConfigurationView> adminConfiguration() {
        return ApiResponse.success(
                diyConfigurationService.getAdminConfiguration(),
                UUID.randomUUID().toString()
        );
    }

    /**
     * 保存尺寸规则。
     *
     * @param request 尺寸规则请求
     * @return 更新后的公开配置视图
     */
    @PutMapping("/config")
    public ApiResponse<DiyConfigurationView> saveRule(
            @Valid @RequestBody SaveDiyRuleConfigRequest request
    ) {
        return ApiResponse.success(
                diyConfigurationService.saveRule(request),
                UUID.randomUUID().toString()
        );
    }

    /** 保存固定视图渲染配置。 */
    @PutMapping("/visual-config")
    public ApiResponse<AdminDiyConfigurationView> saveVisualConfig(
            @Valid @RequestBody SaveDiyVisualConfigRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                diyConfigurationService.saveVisualConfig(
                        request,
                        authentication.getName()
                ),
                UUID.randomUUID().toString()
        );
    }

    /**
     * 整体替换工艺费区间。
     *
     * @param request 工艺费规则请求
     * @return 更新后的公开配置视图
     */
    @PutMapping("/craft-fees")
    public ApiResponse<DiyConfigurationView> replaceCraftFees(
            @Valid @RequestBody SaveCraftFeeRulesRequest request
    ) {
        return ApiResponse.success(
                diyConfigurationService.replaceCraftFeeRules(request.rules()),
                UUID.randomUUID().toString()
        );
    }

    /**
     * 创建绳材选项。
     *
     * @param request 绳材请求
     * @return 更新后的管理配置
     */
    @PostMapping("/cord-options")
    public ApiResponse<AdminDiyConfigurationView> createCordOption(
            @Valid @RequestBody SaveCordOptionRequest request
    ) {
        return ApiResponse.success(
                diyConfigurationService.createCordOption(request),
                UUID.randomUUID().toString()
        );
    }

    /**
     * 更新绳材选项。
     *
     * @param id 绳材主键
     * @param request 绳材请求
     * @return 更新后的管理配置
     */
    @PutMapping("/cord-options/{id}")
    public ApiResponse<AdminDiyConfigurationView> updateCordOption(
            @PathVariable Long id,
            @Valid @RequestBody SaveCordOptionRequest request
    ) {
        return ApiResponse.success(
                diyConfigurationService.updateCordOption(id, request),
                UUID.randomUUID().toString()
        );
    }
}
