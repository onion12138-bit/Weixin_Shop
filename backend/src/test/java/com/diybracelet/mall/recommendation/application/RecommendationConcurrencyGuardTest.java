package com.diybracelet.mall.recommendation.application;

import com.diybracelet.mall.identity.domain.CustomerPrincipal;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** 校验并发容量、顾客重复请求以及异常后的名额归还。 */
class RecommendationConcurrencyGuardTest {
    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sameCustomerCannotStartAnotherRequestEvenWithFreeSlots() {
        var guard = new RecommendationConcurrencyGuard(4);
        login(1L);
        try (var first = guard.acquire()) {
            assertThatThrownBy(guard::acquire).isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).errorCode())
                    .isEqualTo(ErrorCode.AI_RATE_LIMITED);
            login(2L);
            try (var other = guard.acquire()) {
                assertThat(other).isNotNull();
            }
        }
        login(1L);
        try (var retry = guard.acquire()) {
            assertThat(retry).isNotNull();
        }
    }

    @Test
    void duplicateCloseCannotReleaseAnotherRequestsSlot() {
        var guard = new RecommendationConcurrencyGuard(1);
        var first = guard.acquire();
        first.close();
        try (var second = guard.acquire()) {
            first.close();
            assertThatThrownBy(guard::acquire).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void exceptionReturnsPermitImmediately() {
        var guard = new RecommendationConcurrencyGuard(1);
        assertThatThrownBy(() -> {
            try (var lease = guard.acquire()) {
                throw new IllegalStateException("upstream failed");
            }
        }).isInstanceOf(IllegalStateException.class);
        try (var retry = guard.acquire()) {
            assertThat(retry).isNotNull();
        }
    }

    @Test
    void concurrentCallersNeverExceedConfiguredLimit() throws Exception {
        var guard = new RecommendationConcurrencyGuard(2);
        var executor = Executors.newFixedThreadPool(8);
        var attempted = new CountDownLatch(8);
        var release = new CountDownLatch(1);
        var admitted = new AtomicInteger();
        List<Future<?>> calls = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                calls.add(executor.submit(() -> {
                    RecommendationConcurrencyGuard.Lease lease;
                    try {
                        lease = guard.acquire();
                        admitted.incrementAndGet();
                    } catch (BusinessException busy) {
                        attempted.countDown();
                        return;
                    }
                    attempted.countDown();
                    try (lease) {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }));
            }
            assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(admitted.get()).isEqualTo(2);
            release.countDown();
            for (var call : calls) call.get(5, TimeUnit.SECONDS);
            try (var next = guard.acquire()) {
                assertThat(next).isNotNull();
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private void login(long customerId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomerPrincipal(customerId), null, List.of())
        );
    }
}
