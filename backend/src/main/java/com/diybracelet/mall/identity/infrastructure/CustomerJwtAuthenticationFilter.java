package com.diybracelet.mall.identity.infrastructure;

import com.diybracelet.mall.identity.domain.CustomerPrincipal;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从 Authorization Bearer 请求头恢复小程序顾客身份的无状态过滤器。
 *
 * <p>该过滤器只接受 {@code tokenType=CUSTOMER} 的顾客 JWT。管理员 JWT 会被忽略，
 * 随后交给管理员过滤器解析，最终由路由授权规则决定是否允许访问。</p>
 */
@Component
public class CustomerJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final CustomerJwtService customerJwtService;

    public CustomerJwtAuthenticationFilter(CustomerJwtService customerJwtService) {
        this.customerJwtService = customerJwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null
                && authorization.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication()
                == null) {
            String token = authorization.substring(BEARER_PREFIX.length());
            try {
                Long customerIdentityId =
                        customerJwtService.parseCustomerIdentityId(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        new CustomerPrincipal(customerIdentityId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                );
                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
