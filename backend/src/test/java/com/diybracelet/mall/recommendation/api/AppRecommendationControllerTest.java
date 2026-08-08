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
import com.diybracelet.mall.recommendation.api.dto.BirthProfileResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationStatusResponse;
import com.diybracelet.mall.recommendation.application.RecommendationService;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.diybracelet.mall.shared.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Public birthday profile and customer-only recommendation HTTP contracts. */
@WebMvcTest({
    AppRecommendationController.class,
    PublicRecommendationController.class
})
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
    private CustomerJwtService customerJwtService;

    @Autowired
    private JwtService adminJwtService;

    @MockBean
    private RecommendationService service;

    @Test
    void shouldRequireCustomerJwtForAppStatusAndGeneration() throws Exception {
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
    void shouldExposePublicAvailabilityWithoutCreatingACustomerSession()
            throws Exception {
        when(service.status()).thenReturn(
            new RecommendationStatusResponse("AVAILABLE", true)
        );

        mockMvc.perform(get("/api/public/recommendations/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
            .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void shouldExposeTheServerDerivedBirthdayProfileWithoutAuthentication()
            throws Exception {
        when(service.birthProfile("1992-01-20")).thenReturn(profile());

        mockMvc.perform(get("/api/public/recommendations/profile")
                .queryParam("birthDate", "1992-01-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.birthDate").value("1992-01-20"))
            .andExpect(jsonPath("$.data.constellation").value("水瓶座"))
            .andExpect(jsonPath("$.data.zodiac").value("猴"))
            .andExpect(jsonPath("$.data.constellationElement").value("风象"));
    }

    @ParameterizedTest(name = "public birthday error {0}")
    @MethodSource("birthdayErrors")
    void shouldExposePrecisePublicBirthdayErrors(
            ErrorCode errorCode,
            String birthDate
    ) throws Exception {
        when(service.birthProfile(birthDate)).thenThrow(
            new BusinessException(errorCode, "生日无效")
        );

        mockMvc.perform(get("/api/public/recommendations/profile")
                .queryParam("birthDate", birthDate))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(errorCode.name()));
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
    void shouldReturnBirthdayProfileAndMultipleMaterialPlansWithoutPricing()
            throws Exception {
        when(service.recommend(any())).thenReturn(response());

        String body = mockMvc.perform(post("/api/app/recommendations")
                .header("Authorization", customerAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.profile.birthDate").value("1992-01-20"))
            .andExpect(jsonPath("$.data.profile.constellation").value("水瓶座"))
            .andExpect(jsonPath("$.data.plans.length()").value(2))
            .andExpect(jsonPath("$.data.plans[0].schemeName").value("清透通勤"))
            .andExpect(jsonPath("$.data.plans[0].items.length()").value(3))
            .andExpect(jsonPath("$.data.plans[0].items[0].skuId").value(2001))
            .andExpect(jsonPath("$.data.plans[0].items[0].imageUrl")
                .value("/api/public/files/a.png"))
            .andExpect(jsonPath("$.data.culturalDisclaimer", containsString("文化娱乐")))
            .andExpect(jsonPath("$.data.totalAmount").doesNotExist())
            .andExpect(jsonPath("$.data.sequence").doesNotExist())
            .andExpect(jsonPath("$.data.cordOption").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
            .doesNotContain("private wish");
    }

    @Test
    void shouldRejectAnOverlongWishWithoutEchoingIt() throws Exception {
        String sensitiveWish = "愿".repeat(81);
        String requestBody = "{\"birthDate\":\"1992-01-20\",\"wish\":\""
            + sensitiveWish + "\"}";

        mockMvc.perform(post("/api/app/recommendations")
                .header("Authorization", customerAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"))
            .andExpect(content().string(not(containsString(sensitiveWish))));

        verify(service, never()).recommend(any());
    }

    @ParameterizedTest(name = "recommendation error {0}")
    @MethodSource("recommendationErrors")
    void shouldReturnDistinctRecommendationErrorsWithoutPartialData(
            ErrorCode errorCode
    ) throws Exception {
        when(service.recommend(any())).thenThrow(
            new BusinessException(errorCode, "AI 推荐不可用")
        );

        mockMvc.perform(post("/api/app/recommendations")
                .header("Authorization", customerAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().is(errorCode.status().value()))
            .andExpect(jsonPath("$.code").value(errorCode.name()))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    private String customerAuthorization() {
        return "Bearer " + customerJwtService.issue(42L);
    }

    private String validRequest() {
        return """
            {
              "birthDate":"1992-01-20",
              "wishInspiration":"事业",
              "wish":"private wish"
            }
            """;
    }

    private BirthProfileResponse profile() {
        return new BirthProfileResponse("1992-01-20", "水瓶座", "猴", "风象");
    }

    private RecommendationResponse response() {
        List<RecommendationResponse.Item> firstItems = List.of(
            new RecommendationResponse.Item(
                2001L, "白水晶", "/api/public/files/a.png", "清透质感"
            ),
            new RecommendationResponse.Item(
                2002L, "紫水晶", "/api/public/files/b.png", "柔和层次"
            ),
            new RecommendationResponse.Item(
                2003L, "月光石", "/api/public/files/c.png", "日常点缀"
            )
        );
        return new RecommendationResponse(
            profile(),
            List.of(
                new RecommendationResponse.Plan(
                    "清透通勤", "适合日常材质搭配。", firstItems
                ),
                new RecommendationResponse.Plan(
                    "沉静层次",
                    "以沉静材质形成层次。",
                    List.of(firstItems.get(2), firstItems.get(1), firstItems.get(0))
                )
            ),
            "星座、生肖、星座元素及寓意内容仅供文化娱乐与搭配参考。"
        );
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>
            birthdayErrors() {
        return java.util.stream.Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(
                ErrorCode.BIRTH_DATE_REQUIRED, ""
            ),
            org.junit.jupiter.params.provider.Arguments.of(
                ErrorCode.BIRTH_DATE_INVALID, "not-a-date"
            )
        );
    }

    private static java.util.stream.Stream<ErrorCode> recommendationErrors() {
        return java.util.stream.Stream.of(
            ErrorCode.AI_NOT_CONFIGURED,
            ErrorCode.AI_PROMPT_NOT_CONFIGURED,
            ErrorCode.AI_DISABLED,
            ErrorCode.AI_UNAUTHORIZED,
            ErrorCode.AI_MODEL_UNAVAILABLE,
            ErrorCode.AI_RATE_LIMITED,
            ErrorCode.AI_TIMEOUT,
            ErrorCode.AI_UPSTREAM_ERROR,
            ErrorCode.AI_RESPONSE_FORMAT_INVALID,
            ErrorCode.AI_RESULT_COUNT_INVALID,
            ErrorCode.AI_RESULT_ITEM_COUNT_INVALID,
            ErrorCode.AI_RESULT_DUPLICATE_SKU,
            ErrorCode.AI_RESULT_SKU_INVALID,
            ErrorCode.AI_RESPONSE_CONTENT_UNSAFE,
            ErrorCode.BIRTH_DATE_REQUIRED,
            ErrorCode.BIRTH_DATE_INVALID,
            ErrorCode.CATALOG_EMPTY
        );
    }
}
