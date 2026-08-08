package com.diybracelet.mall.recommendation.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diybracelet.mall.auth.infrastructure.JwtAuthenticationFilter;
import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.auth.infrastructure.SecurityConfiguration;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtAuthenticationFilter;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtService;
import com.diybracelet.mall.recommendation.api.dto.AiApiKeyView;
import com.diybracelet.mall.recommendation.api.dto.SaveAiConfigurationRequest;
import com.diybracelet.mall.recommendation.application.AiConfigurationService;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelGateway;
import com.diybracelet.mall.recommendation.infrastructure.AiModelConfigMapper;
import com.diybracelet.mall.recommendation.infrastructure.AiSecretCipher;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.diybracelet.mall.shared.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Admin AI configuration HTTP, security, masking, and lifecycle contract tests. */
@WebMvcTest(AdminAiConfigurationController.class)
@Import({
    AiConfigurationService.class,
    AiSecretCipher.class,
    GlobalExceptionHandler.class,
    JwtService.class,
    JwtAuthenticationFilter.class,
    CustomerJwtService.class,
    CustomerJwtAuthenticationFilter.class,
    SecurityConfiguration.class
})
@TestPropertySource(properties = {
    "app.ai.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "app.auth.jwt-secret=01234567890123456789012345678901",
    "app.auth.jwt-ttl=PT8H",
    "app.auth.jwt-issuer=diy-bracelet-test",
    "app.customer-auth.jwt-secret=customer-test-secret-0123456789012345",
    "app.customer-auth.jwt-ttl=P30D",
    "app.customer-auth.jwt-issuer=diy-customer-test",
    "app.customer-auth.identity-pepper=identity-test-pepper-0123456789012"
})
class AdminAiConfigurationControllerTest {

    private static final String SECRET = "ds-sensitive-admin-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomerJwtService customerJwtService;

    @Autowired
    private AiSecretCipher cipher;

    @MockBean
    private AiModelConfigMapper configMapper;

    @MockBean
    private AiModelGateway modelGateway;

    private AiModelConfig stored;

    @BeforeEach
    void setUpSingletonPersistence() {
        stored = defaultConfig();
        when(configMapper.selectById(1L)).thenAnswer(invocation -> copy(stored));
        when(configMapper.updateWithVersion(any(AiModelConfig.class), anyLong()))
            .thenAnswer(invocation -> {
                AiModelConfig replacement = invocation.getArgument(0);
                long expectedVersion = invocation.getArgument(1);
                if (stored.getVersion() != expectedVersion) {
                    return 0;
                }
                stored = copy(replacement);
                stored.setVersion(expectedVersion + 1);
                return 1;
            });
    }

    @Test
    void shouldRequireAdminAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/ai/config"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"));

        mockMvc.perform(get("/api/admin/ai/config/api-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"));
    }

    @Test
    void shouldForbidAnAuthenticatedCustomerFromAdminConfiguration() throws Exception {
        mockMvc.perform(get("/api/admin/ai/config")
                .header(authHeader(), "Bearer " + customerJwtService.issue(42L)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));

        mockMvc.perform(get("/api/admin/ai/config/api-key")
                .header(authHeader(), "Bearer " + customerJwtService.issue(42L)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));
    }

    @Test
    void shouldReturnDefaultConfigurationWithoutAnySecretField() throws Exception {
        mockMvc.perform(get("/api/admin/ai/config").header(authHeader(), adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.provider").value("DEEPSEEK"))
            .andExpect(jsonPath("$.data.baseUrl").value("https://api.deepseek.com"))
            .andExpect(jsonPath("$.data.modelName").value("deepseek-v4-flash"))
            .andExpect(jsonPath("$.data.apiKeyConfigured").value(false))
            .andExpect(jsonPath("$.data.version").value(0))
            .andExpect(jsonPath("$.data.apiKey").doesNotExist())
            .andExpect(jsonPath("$.data.encryptedApiKey").doesNotExist());
    }

    @Test
    void shouldMaskTheApiKeyInTheSaveRequestStringRepresentation() {
        SaveAiConfigurationRequest request = new SaveAiConfigurationRequest(
            "https://api.deepseek.com",
            "deepseek-chat",
            SECRET,
            true,
            30,
            2048,
            "生成日常材质搭配",
            3,
            0
        );

        org.assertj.core.api.Assertions.assertThat(request.toString())
            .doesNotContain(SECRET)
            .contains("apiKeyProvided=true");
    }

    @Test
    void shouldMaskTheExplicitApiKeyViewStringRepresentation() {
        org.assertj.core.api.Assertions.assertThat(
            new AiApiKeyView(SECRET).toString()
        )
            .doesNotContain(SECRET)
            .contains("apiKeyRedacted=true");
    }

    @Test
    void shouldSaveConfigurationWithoutAKey() throws Exception {
        mockMvc.perform(put("/api/admin/ai/config")
                .header(authHeader(), adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRequest("https://api.deepseek.com/", "deepseek-v4-flash", null, true, 12, 1024, 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.baseUrl").value("https://api.deepseek.com"))
            .andExpect(jsonPath("$.data.modelName").value("deepseek-v4-flash"))
            .andExpect(jsonPath("$.data.apiKeyConfigured").value(false))
            .andExpect(jsonPath("$.data.recommendationPrompt")
                .value("生成日常材质搭配"))
            .andExpect(jsonPath("$.data.planCount").value(3))
            .andExpect(jsonPath("$.data.version").value(1));

        org.assertj.core.api.Assertions.assertThat(stored.getEncryptedApiKey()).isNull();
    }

    @Test
    void shouldEncryptAReplacementKeyWithoutReturningPlaintextOrCiphertext() throws Exception {
        String response = mockMvc.perform(put("/api/admin/ai/config")
                .header(authHeader(), adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRequest("https://api.deepseek.com", "deepseek-v4-flash", SECRET, true, 30, 2048, 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(stored.getEncryptedApiKey())
            .isNotBlank()
            .isNotEqualTo(SECRET);
        org.assertj.core.api.Assertions.assertThat(cipher.decrypt(stored.getEncryptedApiKey()))
            .isEqualTo(SECRET);
        org.assertj.core.api.Assertions.assertThat(response)
            .doesNotContain(SECRET, stored.getEncryptedApiKey(), "Authorization");
    }

    @Test
    void shouldRevealTheStoredKeyOnlyThroughTheExplicitAdminEndpoint() throws Exception {
        stored.setEncryptedApiKey(cipher.encrypt(SECRET));

        mockMvc.perform(get("/api/admin/ai/config/api-key")
                .header(authHeader(), adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.apiKey").value(SECRET))
            .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                result.getResponse().getHeader("Cache-Control")
            ).contains("no-store"));

        mockMvc.perform(get("/api/admin/ai/config")
                .header(authHeader(), adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.apiKey").doesNotExist());
    }

    @Test
    void shouldRejectRevealWhenNoKeyIsConfigured() throws Exception {
        mockMvc.perform(get("/api/admin/ai/config/api-key")
                .header(authHeader(), adminToken()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
    }

    @Test
    void shouldRetainTheExistingKeyWhenSaveKeyIsBlank() throws Exception {
        String encrypted = cipher.encrypt(SECRET);
        stored.setEncryptedApiKey(encrypted);
        stored.setVersion(4L);

        mockMvc.perform(put("/api/admin/ai/config")
                .header(authHeader(), adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRequest("https://api.deepseek.com", "deepseek-v4-flash", "   ", true, 20, 1536, 4)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
            .andExpect(jsonPath("$.data.version").value(5));

        org.assertj.core.api.Assertions.assertThat(stored.getEncryptedApiKey()).isEqualTo(encrypted);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "deepseek-chat",
        "deepseek-v4-pro",
        " deepseek-v4-flash ",
        "DEEPSEEK-V4-FLASH"
    })
    void shouldRejectEveryModelOtherThanTheFixedFlashModel(String modelName) throws Exception {
        mockMvc.perform(put("/api/admin/ai/config")
                .header(authHeader(), adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRequest("https://api.deepseek.com", modelName, "", false, 30, 2048, 0)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"));

        verify(configMapper, never()).updateWithVersion(any(), anyLong());
    }

    @ParameterizedTest(name = "invalid plan count {0}")
    @ValueSource(ints = {0, 6})
    void shouldRejectPlanCountsOutsideOneToFive(int planCount) throws Exception {
        Map<String, Object> request = baseSaveRequest();
        request.put("planCount", planCount);

        mockMvc.perform(put("/api/admin/ai/config")
                .header(authHeader(), adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"));

        verify(configMapper, never()).updateWithVersion(any(), anyLong());
    }

    @Test
    void shouldRejectBlankAndOverlongRecommendationPrompts() throws Exception {
        for (String prompt : List.of("   ", "提".repeat(4001))) {
            Map<String, Object> request = baseSaveRequest();
            request.put("recommendationPrompt", prompt);

            mockMvc.perform(put("/api/admin/ai/config")
                    .header(authHeader(), adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"))
                .andExpect(content().string(not(containsString(prompt))));
        }

        verify(configMapper, never()).updateWithVersion(any(), anyLong());
    }

    @Test
    void shouldClearTheKeyOnlyThroughExplicitDeleteWithVersion() throws Exception {
        stored.setEncryptedApiKey(cipher.encrypt(SECRET));
        stored.setVersion(6L);

        String response = mockMvc.perform(delete("/api/admin/ai/config/api-key")
                .header(authHeader(), adminToken())
                .queryParam("version", "6"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.apiKeyConfigured").value(false))
            .andExpect(jsonPath("$.data.version").value(7))
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(stored.getEncryptedApiKey()).isNull();
        org.assertj.core.api.Assertions.assertThat(response).doesNotContain(SECRET, "Authorization");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://api.deepseek.com",
        "https://api.deepseek.com.evil.test",
        "https://api.deepseek.com@evil.test",
        "https://api.deepseek.com:443",
        "https://api.deepseek.com:",
        "https://api.deepseek.com/v1",
        "https://api.deepseek.com?next=https://evil.test",
        "https://api.deepseek.com#@evil.test",
        "https://api.deepseek.com%2e.evil.test"
    })
    void shouldRejectEveryNonOfficialBaseUrlForm(String baseUrl) throws Exception {
        mockMvc.perform(put("/api/admin/ai/config")
                .header(authHeader(), adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRequest(baseUrl, "deepseek-v4-flash", "", false, 30, 2048, 0)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"))
            .andExpect(content().string(not(containsString(baseUrl))));

        verify(configMapper, never()).updateWithVersion(any(), anyLong());
    }

    @Test
    void shouldRejectAStaleSaveWithoutOverwritingTheCurrentConfiguration() throws Exception {
        stored.setVersion(3L);

        mockMvc.perform(put("/api/admin/ai/config")
                .header(authHeader(), adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRequest("https://api.deepseek.com", "deepseek-v4-flash", SECRET, true, 30, 2048, 2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COMMON_CONFLICT"));

        org.assertj.core.api.Assertions.assertThat(stored.getVersion()).isEqualTo(3L);
        org.assertj.core.api.Assertions.assertThat(stored.getEncryptedApiKey()).isNull();
    }

    @Test
    void shouldReportNotConfiguredBeforeCallingTheGateway() throws Exception {
        stored.setEnabled(true);

        mockMvc.perform(post("/api/admin/ai/config/test")
                .header(authHeader(), adminToken()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));

        verify(modelGateway, never()).listModels(any());
    }

    @Test
    void shouldRejectTestingWhenConfigurationIsDisabled() throws Exception {
        stored.setEncryptedApiKey(cipher.encrypt(SECRET));

        mockMvc.perform(post("/api/admin/ai/config/test")
                .header(authHeader(), adminToken()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI_DISABLED"));
    }

    @Test
    void shouldTestTheConfiguredModelSuccessfully() throws Exception {
        enableWithKey();
        when(modelGateway.listModels(any())).thenReturn(List.of("deepseek-chat", "deepseek-v4-flash"));

        mockMvc.perform(post("/api/admin/ai/config/test")
                .header(authHeader(), adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReportConfiguredModelMissingFromTheProviderList() throws Exception {
        enableWithKey();
        when(modelGateway.listModels(any())).thenReturn(List.of("deepseek-chat"));

        mockMvc.perform(post("/api/admin/ai/config/test")
                .header(authHeader(), adminToken()))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("AI_MODEL_UNAVAILABLE"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatewayFailures")
    void shouldReturnStableGatewayErrors(
            String scenario,
            ErrorCode errorCode,
            int expectedStatus
    ) throws Exception {
        enableWithKey();
        when(modelGateway.listModels(any())).thenThrow(
            new BusinessException(errorCode, "AI 服务请求失败")
        );

        mockMvc.perform(post("/api/admin/ai/config/test")
                .header(authHeader(), adminToken()))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.code").value(errorCode.name()))
            .andExpect(content().string(not(containsString(SECRET))));
    }

    private static Stream<Arguments> gatewayFailures() {
        return Stream.of(
            Arguments.of("upstream 401", ErrorCode.AI_UNAUTHORIZED, 502),
            Arguments.of("upstream 429", ErrorCode.AI_RATE_LIMITED, 503),
            Arguments.of("upstream timeout", ErrorCode.AI_TIMEOUT, 504),
            Arguments.of("upstream 5xx", ErrorCode.AI_UPSTREAM_ERROR, 502)
        );
    }

    private void enableWithKey() {
        stored.setEnabled(true);
        stored.setEncryptedApiKey(cipher.encrypt(SECRET));
    }

    private String saveRequest(
            String baseUrl,
            String modelName,
            String apiKey,
            boolean enabled,
            int timeoutSeconds,
            int maxOutputTokens,
            long version
    ) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("baseUrl", baseUrl);
        request.put("modelName", modelName);
        request.put("apiKey", apiKey);
        request.put("enabled", enabled);
        request.put("timeoutSeconds", timeoutSeconds);
        request.put("maxOutputTokens", maxOutputTokens);
        request.put("recommendationPrompt", "生成日常材质搭配");
        request.put("planCount", 3);
        request.put("version", version);
        return objectMapper.writeValueAsString(request);
    }

    private Map<String, Object> baseSaveRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("baseUrl", "https://api.deepseek.com");
        request.put("modelName", "deepseek-v4-flash");
        request.put("apiKey", "");
        request.put("enabled", true);
        request.put("timeoutSeconds", 30);
        request.put("maxOutputTokens", 2048);
        request.put("recommendationPrompt", "生成日常材质搭配");
        request.put("planCount", 3);
        request.put("version", 0);
        return request;
    }

    private String authHeader() {
        return "Authorization";
    }

    private String adminToken() {
        return "Bearer " + jwtService.issueAccessToken("admin");
    }

    private AiModelConfig defaultConfig() {
        AiModelConfig config = new AiModelConfig();
        config.setId(1L);
        config.setProvider("DEEPSEEK");
        config.setBaseUrl("https://api.deepseek.com");
        config.setModelName("deepseek-v4-flash");
        config.setEncryptedApiKey(null);
        config.setEnabled(false);
        config.setTimeoutSeconds(30);
        config.setMaxOutputTokens(2048);
        config.setRecommendationPrompt("生成日常材质搭配");
        config.setPlanCount(3);
        config.setVersion(0L);
        return config;
    }

    private AiModelConfig copy(AiModelConfig source) {
        AiModelConfig target = new AiModelConfig();
        target.setId(source.getId());
        target.setProvider(source.getProvider());
        target.setBaseUrl(source.getBaseUrl());
        target.setModelName(source.getModelName());
        target.setEncryptedApiKey(source.getEncryptedApiKey());
        target.setEnabled(source.getEnabled());
        target.setTimeoutSeconds(source.getTimeoutSeconds());
        target.setMaxOutputTokens(source.getMaxOutputTokens());
        target.setRecommendationPrompt(source.getRecommendationPrompt());
        target.setPlanCount(source.getPlanCount());
        target.setVersion(source.getVersion());
        return target;
    }
}
