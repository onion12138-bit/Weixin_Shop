package com.diybracelet.mall.auth.infrastructure;

import com.diybracelet.mall.shared.api.ApiResponse;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 后端 HTTP 接口的无状态安全策略。
 *
 * <p>公开目录、公开文件、登录、访客会话与健康检查允许匿名访问；后台接口要求
 * {@code ROLE_ADMIN}，小程序业务接口要求 {@code ROLE_CUSTOMER}。认证与授权失败都返回项目
 * 统一 JSON 结构。</p>
 */
@Configuration
public class SecurityConfiguration {

    /**
     * 禁用 Spring Boot 自动生成的默认内存用户。
     *
     * <p>本项目当前只允许配置化管理员通过 JWT 登录，不开放默认 Basic 账号。</p>
     *
     * @return 不包含任何用户的仓库
     */
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomerJwtAuthenticationFilter customerJwtAuthenticationFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/public/**",
                                "/api/admin/auth/login",
                                "/api/app/session/guest",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/app/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, cause) ->
                                writeError(
                                        response,
                                        objectMapper,
                                        ErrorCode.COMMON_UNAUTHORIZED,
                                        "请先登录"
                                )
                        )
                        .accessDeniedHandler((request, response, cause) ->
                                writeError(
                                        response,
                                        objectMapper,
                                        ErrorCode.COMMON_FORBIDDEN,
                                        "无权访问该资源"
                                )
                        )
                )
                .addFilterBefore(
                        customerJwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }

    /**
     * 允许本地 Vue 开发服务器访问后端 API。
     *
     * <p>来源列表通过环境变量配置，生产部署时应替换为实际后台域名。</p>
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}")
            String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toList()
        );
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private void writeError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            ErrorCode errorCode,
            String message
    ) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(
                        errorCode.name(),
                        message,
                        UUID.randomUUID().toString()
                )
        );
    }
}
