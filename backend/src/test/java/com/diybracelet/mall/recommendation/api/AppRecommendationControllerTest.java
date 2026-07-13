package com.diybracelet.mall.recommendation.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diybracelet.mall.auth.infrastructure.JwtAuthenticationFilter;
import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.auth.infrastructure.SecurityConfiguration;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtAuthenticationFilter;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtService;
import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationStatusResponse;
import com.diybracelet.mall.recommendation.application.RecommendationService;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.diybracelet.mall.shared.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Customer JWT, validation, status and stable error HTTP contract tests. */
@WebMvcTest(AppRecommendationController.class)
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
class AppRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerJwtService customerJwtService;

    @Autowired
    private JwtService adminJwtService;

    @MockBean
    private RecommendationService service;

    @Test
    void shouldRequireCustomerJwtForBothStatusAndGeneration() throws Exception {
        mockMvc.perform(get("/api/app/recommendations/status"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"));

        mockMvc.perform(post("/api/app/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"));

        verify(service, never()).status();
        verify(service, never()).recommend(any());
    }

    @Test
    void shouldForbidAdminJwtFromCustomerRecommendationApis() throws Exception {
        mockMvc.perform(get("/api/app/recommendations/status")
                .header("Authorization", "Bearer "
                    + adminJwtService.issueAccessToken("admin")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));
    }

    @ParameterizedTest(name = "status {0}")
    @ValueSource(strings = {"AVAILABLE", "NOT_CONFIGURED", "DISABLED"})
    void shouldExposeExplicitAvailabilityStatus(String availability) throws Exception {
        when(service.status()).thenReturn(new RecommendationStatusResponse(
            availability,
            "AVAILABLE".equals(availability)
        ));

        mockMvc.perform(get("/api/app/recommendations/status")
                .header("Authorization", customerAuthorization()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value(availability))
            .andExpect(jsonPath("$.data.available")
                .value("AVAILABLE".equals(availability)));
    }

    @Test
    void shouldReturnOneValidatedRecommendationWithoutSensitiveInputEcho() throws Exception {
        when(service.recommend(any())).thenReturn(response());

        String body = mockMvc.perform(post("/api/app/recommendations")
                .header("Authorization", customerAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.schemeName").value("灰蓝通勤"))
            .andExpect(jsonPath("$.data.sequence[0]").value(2001))
            .andExpect(jsonPath("$.data.quantities['2001']").value(2))
            .andExpect(jsonPath("$.data.items[0].imageUrl")
                .value("/api/public/files/a.png"))
            .andExpect(jsonPath("$.data.cordOption.id").value(6001))
            .andExpect(jsonPath("$.data.totalAmount").value(314.80))
            .andExpect(jsonPath("$.data.quoteFingerprint").value("server-fingerprint"))
            .andExpect(jsonPath("$.data.culturalDisclaimer", containsString("文化娱乐参考")))
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
            .doesNotContain("1992-01-20", "private complete consultation text");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"budget\":0,\"wristCircumferenceMm\":160,\"fitType\":\"STANDARD\"}",
        "{\"budget\":100,\"wristCircumferenceMm\":0,\"fitType\":\"STANDARD\"}",
        "{\"budget\":100,\"wristCircumferenceMm\":160}",
        "{\"birthDate\":\"2999-01-01\",\"budget\":100,\"wristCircumferenceMm\":160,\"fitType\":\"STANDARD\"}"
    })
    void shouldRejectOutOfRangeRequiredFields(String requestBody) throws Exception {
        mockMvc.perform(post("/api/app/recommendations")
                .header("Authorization", customerAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"));

        verify(service, never()).recommend(any());
    }

    @Test
    void shouldRejectOverlongStructuredAndFreeTextFieldsWithoutEchoingThem() throws Exception {
        String longConstellation = "星".repeat(17);
        String longWish = "愿".repeat(81);
        String longFreeText = "私".repeat(501);
        String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
            "constellation", longConstellation,
            "wish", longWish,
            "freeText", longFreeText,
            "budget", 100,
            "wristCircumferenceMm", 160,
            "fitType", "STANDARD"
        ));

        mockMvc.perform(post("/api/app/recommendations")
                .header("Authorization", customerAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"))
            .andExpect(content().string(not(containsString(longFreeText))));

        verify(service, never()).recommend(any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "AI_NOT_CONFIGURED",
        "AI_DISABLED",
        "AI_UNAUTHORIZED",
        "AI_MODEL_UNAVAILABLE",
        "AI_RATE_LIMITED",
        "AI_TEMPORARILY_UNAVAILABLE",
        "AI_INVALID_RECOMMENDATION"
    })
    void shouldReturnStableRecommendationErrorsWithoutPartialData(String code) throws Exception {
        ErrorCode errorCode = ErrorCode.valueOf(code);
        when(service.recommend(any())).thenThrow(
            new BusinessException(errorCode, "AI 推荐不可用")
        );

        mockMvc.perform(post("/api/app/recommendations")
                .header("Authorization", customerAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().is(errorCode.status().value()))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    private String customerAuthorization() {
        return "Bearer " + customerJwtService.issue(42L);
    }

    private String validRequest() {
        return """
            {
              "birthDate":"1992-01-20",
              "constellation":"水瓶座",
              "zodiac":"猴",
              "elementPreference":"水",
              "wish":"专注",
              "scene":"通勤",
              "colorPreference":"灰蓝",
              "budget":500,
              "wristCircumferenceMm":160,
              "fitType":"STANDARD",
              "freeText":"private complete consultation text"
            }
            """;
    }

    private RecommendationResponse response() {
        LinkedHashMap<Long, Integer> quantities = new LinkedHashMap<>();
        quantities.put(2001L, 2);
        quantities.put(2002L, 1);
        return new RecommendationResponse(
            "灰蓝通勤",
            List.of(2001L, 2002L, 2001L),
            quantities,
            List.of(
                new RecommendationResponse.Item(
                    2001L,
                    "白水晶 8mm",
                    "/api/public/files/a.png",
                    2,
                    "清透层次"
                ),
                new RecommendationResponse.Item(
                    2002L,
                    "紫水晶 8mm",
                    "/api/public/files/b.png",
                    1,
                    "紫色点缀"
                )
            ),
            "适合通勤。",
            new RecommendationResponse.CordOptionView(
                6001L,
                "ELASTIC-CLEAR",
                "透明弹力绳",
                "#E8E4DC"
            ),
            new BigDecimal("300.00"),
            new BigDecimal("10.00"),
            new BigDecimal("4.80"),
            new BigDecimal("314.80"),
            "server-fingerprint",
            "星座、生肖、五行及寓意内容仅供文化娱乐参考。"
        );
    }
}
