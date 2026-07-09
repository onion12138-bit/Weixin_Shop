package com.diybracelet.mall.identity.infrastructure;

import com.diybracelet.mall.identity.domain.CustomerIdentityResolver;
import com.diybracelet.mall.identity.domain.CustomerPrincipal;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring Security 上下文的顾客身份解析器。
 *
 * <p>业务服务通过 {@link CustomerIdentityResolver} 使用当前顾客身份，不直接依赖
 * 安全框架。测试和后续微信身份合并流程也可以替换该端口。</p>
 */
@Component
public class SecurityCustomerIdentityResolver
        implements CustomerIdentityResolver {

    @Override
    public Long requireCustomerIdentityId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null
                ? null
                : authentication.getPrincipal();
        if (principal instanceof CustomerPrincipal customerPrincipal) {
            return customerPrincipal.customerIdentityId();
        }
        throw new BusinessException(
                ErrorCode.COMMON_UNAUTHORIZED,
                "请先建立访客会话"
        );
    }
}
