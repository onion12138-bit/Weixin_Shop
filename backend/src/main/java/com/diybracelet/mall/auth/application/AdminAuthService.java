package com.diybracelet.mall.auth.application;

import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 本地演示管理员的认证服务。
 *
 * <p>当前阶段账号来自环境配置，适合单管理员产品演示。比较过程使用恒定时间字节比较，
 * 且不会记录密码。正式多账号上线时可将该服务替换为数据库账号和 BCrypt 校验，
 * 控制器与 JWT 安全链无需变化。</p>
 */
@Service
public class AdminAuthService {

    private final String configuredUsername;
    private final String configuredPassword;
    private final JwtService jwtService;

    public AdminAuthService(
            @Value("${app.auth.admin.username}") String configuredUsername,
            @Value("${app.auth.admin.password}") String configuredPassword,
            JwtService jwtService
    ) {
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
        this.jwtService = jwtService;
    }

    /**
     * 校验管理员凭证并签发访问令牌。
     *
     * @param username 登录名
     * @param password 明文登录密码，仅在本方法内用于比较
     * @return 令牌响应
     */
    public LoginResult login(String username, String password) {
        if (!constantTimeEquals(configuredUsername, username)
                || !constantTimeEquals(configuredPassword, password)) {
            throw new BusinessException(
                    ErrorCode.COMMON_UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }
        return new LoginResult(
                jwtService.issueAccessToken(configuredUsername),
                "Bearer",
                jwtService.expiresInSeconds()
        );
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 登录成功后返回给管理后台的最小令牌信息。
     *
     * @param accessToken JWT 字符串
     * @param tokenType 固定为 Bearer
     * @param expiresInSeconds 从签发起计算的有效秒数
     */
    public record LoginResult(
            String accessToken,
            String tokenType,
            long expiresInSeconds
    ) {
    }
}
