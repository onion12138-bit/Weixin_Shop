package com.diybracelet.mall.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证业务异常对外暴露的 HTTP 与 JSON 契约。
 *
 * <p>测试使用最小化的临时控制器主动抛出业务异常，避免依赖任何具体业务模块。
 * 这样商品、订单、会员等模块后续都能复用同一套错误响应结构。</p>
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    /**
     * 业务冲突必须返回 409，并携带稳定业务码、可读消息及非空追踪标识。
     */
    @Test
    void shouldConvertBusinessExceptionToStableResponse() throws Exception {
        mockMvc.perform(get("/test/business-error"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COMMON_CONFLICT"))
            .andExpect(jsonPath("$.message").value("状态冲突"))
            .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    /**
     * 仅服务于异常契约测试的控制器，不会进入生产应用上下文。
     */
    @RestController
    static class FailingController {

        @GetMapping("/test/business-error")
        void fail() {
            throw new BusinessException(ErrorCode.COMMON_CONFLICT, "状态冲突");
        }
    }
}
