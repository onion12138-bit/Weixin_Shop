package com.diybracelet.mall.recommendation.application;

import com.diybracelet.mall.identity.domain.CustomerPrincipal;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** 限制单进程 AI 并发和同一顾客的重复请求，忙时直接拒绝而不排队占用线程。 */
@Component
public class RecommendationConcurrencyGuard {
    private final int maximum;
    private final Set<Long> activeCustomers = new HashSet<>();
    private int activeCount;

    public RecommendationConcurrencyGuard(
            @Value("${app.ai.max-concurrent-requests:4}") int maximum
    ) {
        if (maximum < 1 || maximum > 32) {
            throw new IllegalArgumentException("AI 并发数必须为 1 至 32");
        }
        this.maximum = maximum;
    }

    public synchronized Lease acquire() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Long customerId = authentication != null
                && authentication.getPrincipal() instanceof CustomerPrincipal principal
                ? principal.customerIdentityId() : null;
        if (activeCount >= maximum
                || (customerId != null && activeCustomers.contains(customerId))) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED, "AI 正在处理请求，请稍后重试");
        }
        activeCount++;
        if (customerId != null) activeCustomers.add(customerId);
        return new Lease(customerId);
    }

    /** 成功、失败和异常退出都需归还名额；重复关闭不能释放别人的名额。 */
    public final class Lease implements AutoCloseable {
        private final Long customerId;
        private boolean closed;

        private Lease(Long customerId) {
            this.customerId = customerId;
        }

        @Override
        public void close() {
            synchronized (RecommendationConcurrencyGuard.this) {
                if (closed) return;
                closed = true;
                activeCount--;
                if (customerId != null) activeCustomers.remove(customerId);
            }
        }
    }
}
