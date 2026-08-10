package com.diybracelet.mall.recommendation.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diybracelet.mall.auth.infrastructure.JwtAuthenticationFilter;
import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.auth.infrastructure.SecurityConfiguration;
import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtAuthenticationFilter;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtService;
import com.diybracelet.mall.recommendation.api.dto.AiRecommendationLogView;
import com.diybracelet.mall.recommendation.application.AiRecommendationLogService;
import com.diybracelet.mall.shared.error.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAiRecommendationLogController.class)
@Import({
    GlobalExceptionHandler.class,
    JwtService.class,
    JwtAuthenticationFilter.class,
    CustomerJwtService.class,
    CustomerJwtAuthenticationFilter.class,
    SecurityConfiguration.class
})
@TestPropertySource(properties = {
    "app.auth.jwt-secret=01234567890123456789012345678901",
    "app.auth.jwt-ttl=PT8H",
    "app.auth.jwt-issuer=diy-bracelet-test",
    "app.customer-auth.jwt-secret=customer-test-secret-0123456789012345",
    "app.customer-auth.jwt-ttl=P30D",
    "app.customer-auth.jwt-issuer=diy-customer-test",
    "app.customer-auth.identity-pepper=identity-test-pepper-0123456789012"
})
class AdminAiRecommendationLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AiRecommendationLogService service;

    @Test
    void shouldRequireAdministratorAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/ai/logs"))
            .andExpect(status().isUnauthorized());

        verify(service, never()).list(eq(null), eq(null), eq(1), eq(20));
    }

    @Test
    void shouldListAndReadCompleteTraceMetadata() throws Exception {
        AiRecommendationLogView.Summary summary = summary();
        when(service.list("SUCCEEDED", "蓝色", 1, 20)).thenReturn(
            new PageResponse<>(List.of(summary), 1, 1, 20)
        );
        when(service.detail(7L)).thenReturn(new AiRecommendationLogView.Detail(
            summary,
            null,
            "{\"wish\":\"蓝宝石\"}",
            "{\"preferencePrompt\":\"识别标签\"}",
            "[{\"color\":\"深蓝金星\",\"tags\":[\"深蓝\"]}]",
            "[{\"responseStatus\":200}]",
            "{\"summary\":\"蓝色系\"}",
            "{\"plans\":[]}",
            LocalDateTime.of(2026, 8, 9, 12, 0, 1)
        ));

        mockMvc.perform(get("/api/admin/ai/logs")
                .header("Authorization", adminToken())
                .queryParam("status", "SUCCEEDED")
                .queryParam("keyword", "蓝色"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].traceId").value("trace-blue-7"))
            .andExpect(jsonPath("$.data.items[0].preferenceSummary").value("蓝色系"));

        mockMvc.perform(get("/api/admin/ai/logs/7")
                .header("Authorization", adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.candidatesJson").value(
                "[{\"color\":\"深蓝金星\",\"tags\":[\"深蓝\"]}]"
            ));
    }

    @Test
    void shouldDeleteOneLog() throws Exception {
        mockMvc.perform(delete("/api/admin/ai/logs/7")
                .header("Authorization", adminToken()))
            .andExpect(status().isOk());

        verify(service).delete(7L);
    }

    private AiRecommendationLogView.Summary summary() {
        return new AiRecommendationLogView.Summary(
            7L,
            "trace-blue-7",
            "SUCCEEDED",
            "deepseek-v4-flash",
            "我还想要蓝宝石",
            "蓝色系",
            24,
            3,
            1,
            3,
            1,
            812L,
            null,
            LocalDateTime.of(2026, 8, 9, 12, 0)
        );
    }

    private String adminToken() {
        return "Bearer " + jwtService.issueAccessToken("admin");
    }
}
