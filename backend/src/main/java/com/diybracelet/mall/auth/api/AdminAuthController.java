package com.diybracelet.mall.auth.api;

import com.diybracelet.mall.auth.application.AdminAuthService;
import com.diybracelet.mall.auth.application.AdminAuthService.LoginResult;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 管理后台本地账号登录接口。
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    /**
     * 校验演示管理员凭证并返回 JWT。
     *
     * @param request 登录请求
     * @return 统一成功响应
     */
    @PostMapping("/login")
    public ApiResponse<LoginResult> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResult result = adminAuthService.login(
                request.username(),
                request.password()
        );
        return ApiResponse.success(result, UUID.randomUUID().toString());
    }

    /**
     * 管理员登录请求。
     *
     * @param username 登录名
     * @param password 登录密码
     */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空")
            String username,
            @NotBlank(message = "密码不能为空")
            String password
    ) {
    }
}
