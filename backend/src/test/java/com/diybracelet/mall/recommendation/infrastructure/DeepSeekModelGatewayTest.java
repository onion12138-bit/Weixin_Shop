package com.diybracelet.mall.recommendation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelExchange;
import com.diybracelet.mall.recommendation.domain.ModelRecommendation;
import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.recommendation.domain.RecommendationProfile;
import com.diybracelet.mall.recommendation.domain.RecommendationRepairFeedback;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** HTTP boundary, strict JSON, safe prompt, and distinct error mapping tests. */
class DeepSeekModelGatewayTest {

    private static final String SECRET = "ds-sensitive-gateway-key";
    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(
        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    void shouldWireTheProductionGatewayInASpringContext() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(
                AiSecretCipher.class,
                () -> new AiSecretCipher(MASTER_KEY)
            );
            context.register(DeepSeekModelGateway.class);

            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.getBean(DeepSeekModelGateway.class)).isNotNull();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCallModelsOnceWithBearerAuthenticationAndTimeout() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = response(
            200,
            "{\"data\":[{\"id\":\"deepseek-chat\"},{\"id\":\"deepseek-v4-flash\"}]}"
        );
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);

        assertThat(gateway(client).listModels(config(17, 2)))
            .containsExactly("deepseek-chat", "deepseek-v4-flash");

        ArgumentCaptor<HttpRequest> requestCaptor =
            ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(
            requestCaptor.capture(), any(HttpResponse.BodyHandler.class)
        );
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.uri().toString()).isEqualTo("https://api.deepseek.com/models");
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.timeout()).contains(Duration.ofSeconds(17));
        assertThat(request.headers().firstValue("Authorization"))
            .contains("Bearer " + SECRET);
    }

    @ParameterizedTest(name = "models HTTP {0}")
    @MethodSource("upstreamStatuses")
    @SuppressWarnings("unchecked")
    void shouldMapModelDiscoveryStatusesToDistinctSafeErrors(
            int status,
            ErrorCode expectedCode
    ) throws Exception {
        String sensitiveBody = "sensitive-models-body-" + status;
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> upstreamResponse = response(status, sensitiveBody);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(upstreamResponse);

        assertThatThrownBy(() -> gateway(client).listModels(config(10, 2)))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(expectedCode))
            .hasMessageNotContaining(sensitiveBody)
            .hasMessageNotContaining(SECRET);
        verify(client, times(1)).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMapTimeoutSeparatelyWithoutRetryOrSensitiveDetail() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new HttpTimeoutException("sensitive-timeout-detail"));

        assertThatThrownBy(() -> gateway(client).listModels(config(1, 2)))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AI_TIMEOUT))
            .hasMessageNotContaining("sensitive-timeout-detail")
            .hasMessageNotContaining(SECRET);
        verify(client, times(1)).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMapIoFailureAndMalformedModelsBodyToUpstreamError() throws Exception {
        HttpClient ioClient = mock(HttpClient.class);
        when(ioClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("sensitive-io-detail"));
        assertCode(
            () -> gateway(ioClient).listModels(config(10, 2)),
            ErrorCode.AI_UPSTREAM_ERROR
        );

        HttpClient malformedClient = mock(HttpClient.class);
        HttpResponse<String> malformedResponse = response(200, "sensitive-not-json");
        when(malformedClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(malformedResponse);
        assertCode(
            () -> gateway(malformedClient).listModels(config(10, 2)),
            ErrorCode.AI_UPSTREAM_ERROR
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldControlInvalidHeaderValuesWithoutSendingOrExposingTheKey()
            throws Exception {
        HttpClient client = mock(HttpClient.class);
        String invalidKey = "sensitive\r\nInjected: value";

        assertThatThrownBy(() -> gateway(client).listModels(config(10, 2, invalidKey)))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AI_UPSTREAM_ERROR))
            .hasMessageNotContaining(invalidKey)
            .hasMessageNotContaining("Injected");
        verify(client, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCallChatWithBirthdayProfileBusinessPromptCandidatesAndPlanCount()
            throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> chatResponse = response(
            200, chatEnvelope(validContent(2))
        );
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(chatResponse);

        ModelRecommendation result = gateway(client).recommend(
            config(17, 2), profile(), candidates(), null
        );

        assertThat(result.plans()).hasSize(2);
        assertThat(result.plans().get(0).items())
            .extracting(ModelRecommendation.Item::skuId)
            .containsExactly(2001L, 2002L, 2003L);

        ArgumentCaptor<HttpRequest> requestCaptor =
            ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), any());
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.uri().toString())
            .isEqualTo("https://api.deepseek.com/chat/completions");
        assertThat(request.timeout()).contains(Duration.ofSeconds(17));
        JsonNode root = new ObjectMapper().readTree(requestBody(request));
        assertThat(root.path("model").asText()).isEqualTo("deepseek-v4-flash");
        assertThat(root.path("temperature").decimalValue()).isEqualByComparingTo("0");
        assertThat(root.path("max_tokens").asInt()).isEqualTo(2048);
        assertThat(root.path("thinking").path("type").asText())
            .isEqualTo("disabled");
        assertThat(root.path("response_format").path("type").asText())
            .isEqualTo("json_object");

        JsonNode prompt = new ObjectMapper().readTree(
            root.path("messages").get(1).path("content").asText()
        );
        assertThat(prompt.path("businessPrompt").asText())
            .isEqualTo("生成日常材质搭配");
        assertThat(prompt.path("preferencePrompt").asText())
            .contains("颜色和标签");
        assertThat(prompt.path("requiredPlanCount").asInt()).isEqualTo(2);
        assertThat(prompt.path("repair").isMissingNode()).isTrue();
        assertThat(prompt.path("exactOutputExample").path("plans")).hasSize(2);
        assertThat(prompt.path("exactOutputExample").path("plans").get(0).path("items"))
            .hasSize(3);
        assertThat(prompt.path("exactOutputExample").path("plans").get(0).path("items"))
            .extracting(item -> item.path("skuId").asLong())
            .containsExactly(2001L, 2002L, 2003L);
        assertThat(prompt.path("exactOutputExample").path("plans").get(1).path("items"))
            .extracting(item -> item.path("skuId").asLong())
            .containsExactly(2002L, 2003L, 2004L);
        assertThat(prompt.path("customerProfile").path("birthDate").asText())
            .isEqualTo("1992-01-20");
        assertThat(prompt.path("customerProfile").path("constellation").asText())
            .isEqualTo("水瓶座");
        assertThat(prompt.path("customerProfile").path("zodiac").asText())
            .isEqualTo("猴");
        assertThat(prompt.path("customerProfile").path("constellationElement").asText())
            .isEqualTo("风象");
        assertThat(prompt.path("customerProfile").path("wishInspiration").asText())
            .isEqualTo("事业");
        assertThat(prompt.path("customerProfile").path("wish").asText())
            .isEqualTo("private wish");
        assertThat(prompt.path("candidates").get(0).fieldNames()).toIterable()
            .containsExactlyInAnyOrder(
                "skuId", "name", "material", "color", "diameterMm", "tags"
            );
        assertThat(prompt.path("candidates").get(0).path("color").asText())
            .isEqualTo("灰蓝");
        assertThat(prompt.path("candidates").get(0).path("tags"))
            .extracting(JsonNode::asText)
            .containsExactly("日常");
        assertThat(requestBody(request))
            .doesNotContain(
                "salePrice", "costPrice", "availableQty", "imageUrl",
                "/private/image.png"
            );
        assertThat(profile().toString())
            .doesNotContain("1992-01-20", "private wish")
            .contains("sensitiveFieldsRedacted=true");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCaptureRawExchangeAndParseOptionalPreferenceAnalysis() throws Exception {
        HttpClient client = mock(HttpClient.class);
        String content = validContent(2).replaceFirst(
            "\\{",
            "{\"preferenceAnalysis\":{\"summary\":\"蓝色系\","
                + "\"matchedCandidateIds\":[2001,2002],"
                + "\"evidence\":[\"2001：color=灰蓝\",\"2002：tags=日常\"]},"
        );
        HttpResponse<String> upstreamResponse = response(
            200, chatEnvelope(content)
        );
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(upstreamResponse);
        List<AiModelExchange> exchanges = new ArrayList<>();

        ModelRecommendation result = gateway(client).recommend(
            config(17, 2), profile(), candidates(), null, exchanges::add
        );

        assertThat(result.preferenceAnalysis().summary()).isEqualTo("蓝色系");
        assertThat(result.preferenceAnalysis().matchedCandidateIds())
            .containsExactly(2001L, 2002L);
        assertThat(exchanges).singleElement().satisfies(exchange -> {
            assertThat(exchange.responseStatus()).isEqualTo(200);
            assertThat(exchange.requestJson()).contains("preferencePrompt");
            assertThat(exchange.responseBody()).contains("蓝色系");
            assertThat(exchange.toString()).doesNotContain("private wish", SECRET);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendOnlyTheAllowListedRepairCodeWithoutPriorModelText() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> chatResponse = response(
            200, chatEnvelope(validContent(2))
        );
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(chatResponse);

        gateway(client).recommend(
            config(10, 2),
            profile(),
            candidates(),
            new RecommendationRepairFeedback("AI_RESULT_COUNT_INVALID")
        );

        ArgumentCaptor<HttpRequest> requestCaptor =
            ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), any());
        JsonNode request = new ObjectMapper().readTree(
            requestBody(requestCaptor.getValue())
        );
        JsonNode prompt = new ObjectMapper().readTree(
            request.path("messages").get(1).path("content").asText()
        );
        assertThat(prompt.path("repair").path("previousResultRejected").asBoolean())
            .isTrue();
        assertThat(prompt.path("repair").path("code").asText())
            .isEqualTo("AI_RESULT_COUNT_INVALID");
        assertThat(prompt.path("repair").path("requiredPlanCount").asInt())
            .isEqualTo(2);
        assertThat(prompt.path("repair").path("requiredItemsPerPlan").asInt())
            .isEqualTo(3);
        assertThat(
            prompt.path("repair").path("requireUniqueSkuIdsWithinPlan").asBoolean()
        ).isTrue();
        assertThat(prompt.path("repair").size()).isEqualTo(5);
        assertThat(request.toString())
            .doesNotContain("prior model output", SECRET);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRequireExactlyThreeItemsWhenRepairingItemCount() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> chatResponse = response(
            200, chatEnvelope(validContent(2))
        );
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(chatResponse);

        gateway(client).recommend(
            config(10, 2),
            profile(),
            candidates(),
            new RecommendationRepairFeedback("AI_RESULT_ITEM_COUNT_INVALID")
        );

        ArgumentCaptor<HttpRequest> requestCaptor =
            ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), any());
        JsonNode request = new ObjectMapper().readTree(
            requestBody(requestCaptor.getValue())
        );
        JsonNode prompt = new ObjectMapper().readTree(
            request.path("messages").get(1).path("content").asText()
        );
        assertThat(prompt.path("repair").path("requiredItemsPerPlan").asInt())
            .isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRequireUniqueSkuIdsWhenRepairingDuplicates() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> chatResponse = response(
            200, chatEnvelope(validContent(2))
        );
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(chatResponse);

        gateway(client).recommend(
            config(10, 2),
            profile(),
            candidates(),
            new RecommendationRepairFeedback("AI_RESULT_DUPLICATE_SKU")
        );

        ArgumentCaptor<HttpRequest> requestCaptor =
            ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), any());
        JsonNode request = new ObjectMapper().readTree(
            requestBody(requestCaptor.getValue())
        );
        JsonNode prompt = new ObjectMapper().readTree(
            request.path("messages").get(1).path("content").asText()
        );
        assertThat(
            prompt.path("repair").path("requireUniqueSkuIdsWithinPlan").asBoolean()
        ).isTrue();
    }

    @ParameterizedTest(name = "invalid chat: {0}")
    @MethodSource("invalidChatContents")
    @SuppressWarnings("unchecked")
    void shouldRejectMalformedOrOutOfContractModelJsonWithDistinctCode(
            String scenario,
            String content,
            ErrorCode expectedCode
    ) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> chatResponse = response(200, chatEnvelope(content));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(chatResponse);

        assertThatThrownBy(() -> gateway(client).recommend(
            config(10, 2), profile(), candidates(), null
        ))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(expectedCode))
            .hasMessageNotContaining(content)
            .hasMessageNotContaining(SECRET);
        verify(client, times(1)).send(any(), any());
    }

    @ParameterizedTest(name = "chat HTTP {0}")
    @MethodSource("upstreamStatuses")
    @SuppressWarnings("unchecked")
    void shouldMapChatStatusesWithoutAutomaticRetry(
            int status,
            ErrorCode expectedCode
    ) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> upstreamResponse = response(
            status, "sensitive-chat-body"
        );
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(upstreamResponse);

        assertCode(
            () -> gateway(client).recommend(
                config(10, 2), profile(), candidates(), null
            ),
            expectedCode
        );
        verify(client, times(1)).send(any(), any());
    }

    private static Stream<Arguments> upstreamStatuses() {
        return Stream.of(
            Arguments.of(400, ErrorCode.AI_MODEL_UNAVAILABLE),
            Arguments.of(401, ErrorCode.AI_UNAUTHORIZED),
            Arguments.of(403, ErrorCode.AI_UNAUTHORIZED),
            Arguments.of(404, ErrorCode.AI_MODEL_UNAVAILABLE),
            Arguments.of(429, ErrorCode.AI_RATE_LIMITED),
            Arguments.of(500, ErrorCode.AI_UPSTREAM_ERROR),
            Arguments.of(503, ErrorCode.AI_UPSTREAM_ERROR)
        );
    }

    private static Stream<Arguments> invalidChatContents() {
        String onePlan = """
            {"plans":[{"schemeName":"A","summary":"S","items":[
              {"skuId":2001,"reason":"a"},{"skuId":2002,"reason":"b"},
              {"skuId":2003,"reason":"c"}]}]}
            """;
        String unknownSku = validContent(2).replaceFirst("2001", "9999");
        return Stream.of(
            Arguments.of("not json", "not-json", ErrorCode.AI_RESPONSE_FORMAT_INVALID),
            Arguments.of(
                "wrong plan count", onePlan, ErrorCode.AI_RESULT_COUNT_INVALID
            ),
            Arguments.of(
                "unknown sku", unknownSku, ErrorCode.AI_RESULT_SKU_INVALID
            ),
            Arguments.of(
                "extra root field",
                validContent(2).replaceFirst("\\{", "{\"extra\":true,"),
                ErrorCode.AI_RESPONSE_FORMAT_INVALID
            ),
            Arguments.of(
                "duplicate item sku",
                validContent(2).replaceFirst("2002", "2001"),
                ErrorCode.AI_RESULT_DUPLICATE_SKU
            ),
            Arguments.of(
                "too few items",
                validContent(2).replaceFirst(
                    ",\\{\"skuId\":2003,\"reason\":\"c\"}", ""
                ),
                ErrorCode.AI_RESULT_ITEM_COUNT_INVALID
            ),
            Arguments.of(
                "trailing token",
                validContent(2) + " 42",
                ErrorCode.AI_RESPONSE_FORMAT_INVALID
            )
        );
    }

    private static String validContent(int planCount) {
        String first = """
            {"schemeName":"清透通勤","summary":"日常搭配。","items":[
              {"skuId":2001,"reason":"a"},{"skuId":2002,"reason":"b"},
              {"skuId":2003,"reason":"c"}]}
            """.replaceAll("\\s+", "");
        String second = """
            {"schemeName":"沉静层次","summary":"沉静搭配。","items":[
              {"skuId":2003,"reason":"c"},{"skuId":2004,"reason":"d"},
              {"skuId":2005,"reason":"e"}]}
            """.replaceAll("\\s+", "");
        return switch (planCount) {
            case 1 -> "{\"plans\":[" + first + "]}";
            case 2 -> "{\"plans\":[" + first + "," + second + "]}";
            default -> throw new IllegalArgumentException("unsupported test plan count");
        };
    }

    private DeepSeekModelGateway gateway(HttpClient client) {
        return new DeepSeekModelGateway(
            client,
            new ObjectMapper(),
            new AiSecretCipher(MASTER_KEY)
        );
    }

    private RecommendationProfile profile() {
        return new RecommendationProfile(
            "1992-01-20", "水瓶座", "猴", "风象", "事业", "private wish"
        );
    }

    private List<RecommendationCandidate> candidates() {
        return Stream.of(2001L, 2002L, 2003L, 2004L, 2005L)
            .map(id -> new RecommendationCandidate(
                id,
                "珠子 " + id,
                "天然水晶",
                "灰蓝",
                new BigDecimal("8.00"),
                new BigDecimal("10.00"),
                "[\"日常\"]",
                200,
                "/private/image.png"
            ))
            .toList();
    }

    private AiModelConfig config(int timeoutSeconds, int planCount) {
        return config(timeoutSeconds, planCount, SECRET);
    }

    private AiModelConfig config(
            int timeoutSeconds,
            int planCount,
            String apiKey
    ) {
        AiSecretCipher cipher = new AiSecretCipher(MASTER_KEY);
        AiModelConfig config = new AiModelConfig();
        config.setId(1L);
        config.setProvider("DEEPSEEK");
        config.setBaseUrl("https://api.deepseek.com");
        config.setModelName("deepseek-v4-flash");
        config.setEncryptedApiKey(cipher.encrypt(apiKey));
        config.setEnabled(true);
        config.setTimeoutSeconds(timeoutSeconds);
        config.setMaxOutputTokens(2048);
        config.setRecommendationPrompt("  生成日常材质搭配  ");
        config.setPreferencePrompt("  优先识别颜色和标签偏好  ");
        config.setSoftPreferenceEnabled(true);
        config.setPlanCount(planCount);
        config.setVersion(0L);
        return config;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private String chatEnvelope(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(java.util.Map.of(
            "choices",
            List.of(java.util.Map.of(
                "message", java.util.Map.of("content", content)
            ))
        ));
    }

    private String requestBody(HttpRequest request) {
        BodyCollector collector = new BodyCollector();
        request.bodyPublisher().orElseThrow().subscribe(collector);
        return collector.body();
    }

    private void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            ErrorCode expectedCode
    ) {
        assertThatThrownBy(call)
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(expectedCode))
            .hasMessageNotContaining(SECRET)
            .hasMessageNotContaining("sensitive");
    }

    private static final class BodyCollector
            implements Flow.Subscriber<java.nio.ByteBuffer> {

        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(java.nio.ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            output.writeBytes(bytes);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError(throwable);
        }

        @Override
        public void onComplete() {
        }

        private String body() {
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}
