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
import com.diybracelet.mall.diy.domain.FitType;
import com.diybracelet.mall.recommendation.domain.ModelRecommendation;
import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.recommendation.domain.RecommendationProfile;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** DeepSeek HTTP adapter tests that never use a real network or API key. */
class DeepSeekModelGatewayTest {

    private static final String SECRET = "ds-sensitive-gateway-secret";
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
    void shouldCallModelsOnceWithBearerAuthenticationAndRequestTimeout() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"data\":[{\"id\":\"deepseek-chat\"},{\"id\":\"deepseek-v4-flash\"}]}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);
        DeepSeekModelGateway gateway = gateway(client);

        List<String> models = gateway.listModels(config(17));

        assertThat(models).containsExactly("deepseek-chat", "deepseek-v4-flash");
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(1)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.uri().toString()).isEqualTo("https://api.deepseek.com/models");
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.timeout()).contains(Duration.ofSeconds(17));
        assertThat(request.headers().firstValue("Authorization"))
            .contains("Bearer " + SECRET);
    }

    @ParameterizedTest(name = "upstream HTTP {0}")
    @MethodSource("statusMappings")
    @SuppressWarnings("unchecked")
    void shouldMapUpstreamStatusesWithoutReturningTheBody(
            int upstreamStatus,
            ErrorCode expectedCode
    ) throws Exception {
        String upstreamBody = "upstream-sensitive-body-" + upstreamStatus;
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(upstreamStatus);
        when(response.body()).thenReturn(upstreamBody);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);

        assertThatThrownBy(() -> gateway(client).listModels(config(10)))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(expectedCode))
            .hasMessageNotContaining(upstreamBody)
            .hasMessageNotContaining(SECRET)
            .hasMessageNotContaining("Authorization");
        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMapTimeoutWithoutRetryingOrExposingTheCauseMessage() throws Exception {
        HttpClient client = mock(HttpClient.class);
        String sensitiveCause = "timeout-upstream-sensitive-body";
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new HttpTimeoutException(sensitiveCause));

        assertThatThrownBy(() -> gateway(client).listModels(config(1)))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AI_TEMPORARILY_UNAVAILABLE))
            .hasMessageNotContaining(sensitiveCause)
            .hasMessageNotContaining(SECRET)
            .hasMessageNotContaining("Authorization");
        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldTreatMalformedSuccessBodyAsTemporaryUnavailability() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("upstream-sensitive-not-json");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);

        assertThatThrownBy(() -> gateway(client).listModels(config(10)))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AI_TEMPORARILY_UNAVAILABLE))
            .hasMessageNotContaining("upstream-sensitive-not-json")
            .hasMessageNotContaining(SECRET);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldControlInvalidHeaderValuesWithoutSendingOrExposingTheKey() throws Exception {
        HttpClient client = mock(HttpClient.class);
        String invalidKey = "sensitive-key\r\nInjected: value";

        assertThatThrownBy(() -> gateway(client).listModels(config(10, invalidKey)))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AI_TEMPORARILY_UNAVAILABLE))
            .hasMessageNotContaining(invalidKey)
            .hasMessageNotContaining("Injected");
        verify(client, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCallChatOnceWithControlledJsonAndConfiguredLimits() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(chatEnvelope("""
            {"schemeName":"灰蓝通勤","sequence":[2001,2002,2001],
             "reasons":[{"skuId":2001,"reason":"清透层次"},{"skuId":2002,"reason":"紫色点缀"}],
             "summary":"适合日常通勤。"}
            """));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);
        RecommendationProfile profile = profile("private complete consultation text");

        ModelRecommendation result = gateway(client).recommend(
            config(17), profile, candidates()
        );

        assertThat(result.sequence()).containsExactly(2001L, 2002L, 2001L);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(1)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertThat(request.uri().toString())
            .isEqualTo("https://api.deepseek.com/chat/completions");
        assertThat(request.timeout()).contains(Duration.ofSeconds(17));
        String json = requestBody(request);
        JsonNode root = new ObjectMapper().readTree(json);
        assertThat(root.path("model").asText()).isEqualTo("deepseek-v4-flash");
        assertThat(root.path("temperature").decimalValue()).isEqualByComparingTo("0.2");
        assertThat(root.path("max_tokens").asInt()).isEqualTo(2048);
        assertThat(root.path("response_format").path("type").asText())
            .isEqualTo("json_object");
        JsonNode prompt = new ObjectMapper().readTree(
            root.path("messages").get(1).path("content").asText()
        );
        assertThat(prompt.path("profile").has("birthDate")).isFalse();
        assertThat(prompt.path("profile").path("freeText").asText())
            .isEqualTo("private complete consultation text");
        assertThat(prompt.path("candidates").get(0).fieldNames()).toIterable()
            .containsExactlyInAnyOrder(
                "skuId", "name", "material", "color", "diameterMm",
                "salePrice", "tags", "availableQty"
            );
        assertThat(json).doesNotContain(
            "1992-01-20", "costPrice", "cost_price", "/private/image.png"
        );
        assertThat(profile.toString())
            .doesNotContain("private complete consultation text", "birthDate", "freeText")
            .contains("sensitiveFieldsRedacted=true");
    }

    @ParameterizedTest(name = "invalid chat: {0}")
    @MethodSource("invalidChatContents")
    @SuppressWarnings("unchecked")
    void shouldStrictlyRejectMalformedOrOutOfContractModelJson(
            String scenario,
            String content
    ) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(chatEnvelope(content));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);

        assertThatThrownBy(() -> gateway(client).recommend(
                config(10), profile(null), candidates()
            ))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AI_INVALID_RECOMMENDATION))
            .hasMessageNotContaining(content)
            .hasMessageNotContaining(SECRET);
        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @ParameterizedTest(name = "chat upstream {0}")
    @MethodSource("chatStatusMappings")
    @SuppressWarnings("unchecked")
    void shouldMapChatUpstreamWithoutRetryOrSensitiveBody(
            int status,
            ErrorCode expectedCode
    ) throws Exception {
        String sensitiveBody = "sensitive-chat-upstream-" + status;
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(sensitiveBody);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);

        assertThatThrownBy(() -> gateway(client).recommend(
                config(10), profile(null), candidates()
            ))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(expectedCode))
            .hasMessageNotContaining(sensitiveBody)
            .hasMessageNotContaining(SECRET);
        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMapChatTimeoutWithoutRetryOrSensitiveInput() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new HttpTimeoutException("sensitive-timeout-body"));

        assertThatThrownBy(() -> gateway(client).recommend(
                config(1), profile("private complete consultation text"), candidates()
            ))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AI_TEMPORARILY_UNAVAILABLE))
            .hasMessageNotContaining("sensitive-timeout-body")
            .hasMessageNotContaining("private complete consultation text")
            .hasMessageNotContaining(SECRET);
        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static Stream<Arguments> statusMappings() {
        return Stream.of(
            Arguments.of(401, ErrorCode.AI_UNAUTHORIZED),
            Arguments.of(403, ErrorCode.AI_UNAUTHORIZED),
            Arguments.of(429, ErrorCode.AI_RATE_LIMITED),
            Arguments.of(500, ErrorCode.AI_TEMPORARILY_UNAVAILABLE),
            Arguments.of(503, ErrorCode.AI_TEMPORARILY_UNAVAILABLE)
        );
    }

    private static Stream<Arguments> chatStatusMappings() {
        return Stream.of(
            Arguments.of(400, ErrorCode.AI_MODEL_UNAVAILABLE),
            Arguments.of(401, ErrorCode.AI_UNAUTHORIZED),
            Arguments.of(403, ErrorCode.AI_UNAUTHORIZED),
            Arguments.of(404, ErrorCode.AI_MODEL_UNAVAILABLE),
            Arguments.of(429, ErrorCode.AI_RATE_LIMITED),
            Arguments.of(500, ErrorCode.AI_TEMPORARILY_UNAVAILABLE)
        );
    }

    private static Stream<Arguments> invalidChatContents() {
        String tooMany = Stream.generate(() -> "2001")
            .limit(201)
            .collect(java.util.stream.Collectors.joining(","));
        return Stream.of(
            Arguments.of("not json", "not-json"),
            Arguments.of("duplicate root field", "{\"schemeName\":\"first\",\"schemeName\":\"second\",\"sequence\":[2001],\"reasons\":[{\"skuId\":2001,\"reason\":\"x\"}],\"summary\":\"x\"}"),
            Arguments.of("duplicate nested reason field", "{\"schemeName\":\"x\",\"sequence\":[2001],\"reasons\":[{\"skuId\":2001,\"reason\":\"first\",\"reason\":\"second\"}],\"summary\":\"x\"}"),
            Arguments.of("trailing second root", "{\"schemeName\":\"x\",\"sequence\":[2001],\"reasons\":[{\"skuId\":2001,\"reason\":\"x\"}],\"summary\":\"x\"} {\"second\":true}"),
            Arguments.of("trailing scalar token", "{\"schemeName\":\"x\",\"sequence\":[2001],\"reasons\":[{\"skuId\":2001,\"reason\":\"x\"}],\"summary\":\"x\"} 42"),
            Arguments.of("missing field", "{\"schemeName\":\"x\",\"sequence\":[2001],\"reasons\":[]}"),
            Arguments.of("model price field", "{\"schemeName\":\"x\",\"sequence\":[2001],\"reasons\":[{\"skuId\":2001,\"reason\":\"x\"}],\"summary\":\"x\",\"totalAmount\":0.01}"),
            Arguments.of("nonpositive quantity field", "{\"schemeName\":\"x\",\"sequence\":[2001],\"quantities\":{\"2001\":-1},\"reasons\":[{\"skuId\":2001,\"reason\":\"x\"}],\"summary\":\"x\"}"),
            Arguments.of("empty sequence", "{\"schemeName\":\"x\",\"sequence\":[],\"reasons\":[],\"summary\":\"x\"}"),
            Arguments.of("too many beads", "{\"schemeName\":\"x\",\"sequence\":[" + tooMany + "],\"reasons\":[{\"skuId\":2001,\"reason\":\"x\"}],\"summary\":\"x\"}"),
            Arguments.of("unknown sku", "{\"schemeName\":\"x\",\"sequence\":[9999],\"reasons\":[{\"skuId\":9999,\"reason\":\"x\"}],\"summary\":\"x\"}"),
            Arguments.of("unselected reason", "{\"schemeName\":\"x\",\"sequence\":[2001],\"reasons\":[{\"skuId\":2002,\"reason\":\"x\"}],\"summary\":\"x\"}"),
            Arguments.of("blank scheme", "{\"schemeName\":\" \",\"sequence\":[2001],\"reasons\":[{\"skuId\":2001,\"reason\":\"x\"}],\"summary\":\"x\"}"),
            Arguments.of("long scheme", "{\"schemeName\":\"" + "a".repeat(81) + "\",\"sequence\":[2001],\"reasons\":[{\"skuId\":2001,\"reason\":\"x\"}],\"summary\":\"x\"}"),
            Arguments.of("long reason", "{\"schemeName\":\"x\",\"sequence\":[2001],\"reasons\":[{\"skuId\":2001,\"reason\":\"" + "a".repeat(301) + "\"}],\"summary\":\"x\"}"),
            Arguments.of("long summary", "{\"schemeName\":\"x\",\"sequence\":[2001],\"reasons\":[{\"skuId\":2001,\"reason\":\"x\"}],\"summary\":\"" + "a".repeat(1001) + "\"}")
        );
    }

    private DeepSeekModelGateway gateway(HttpClient client) {
        return new DeepSeekModelGateway(client, new ObjectMapper(), new AiSecretCipher(MASTER_KEY));
    }

    private RecommendationProfile profile(String freeText) {
        return new RecommendationProfile(
            "水瓶座",
            "猴",
            "水",
            "专注",
            "通勤",
            "灰蓝",
            new BigDecimal("500.00"),
            new BigDecimal("160.00"),
            FitType.STANDARD,
            freeText
        );
    }

    private List<RecommendationCandidate> candidates() {
        return List.of(
            new RecommendationCandidate(
                2001L, "白水晶 8mm", "天然水晶", "透明",
                new BigDecimal("8.00"), new BigDecimal("12.80"),
                "[\"专注\",\"日常\"]", 20, "/private/image.png"
            ),
            new RecommendationCandidate(
                2002L, "紫水晶 8mm", "天然水晶", "紫色",
                new BigDecimal("8.00"), new BigDecimal("16.80"),
                "[\"通勤\"]", 20, "/private/image-2.png"
            )
        );
    }

    private String chatEnvelope(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(java.util.Map.of(
            "choices", List.of(java.util.Map.of(
                "message", java.util.Map.of("content", content)
            ))
        ));
    }

    private String requestBody(HttpRequest request) {
        BodyCollector collector = new BodyCollector();
        request.bodyPublisher().orElseThrow().subscribe(collector);
        return collector.body();
    }

    private AiModelConfig config(int timeoutSeconds) {
        return config(timeoutSeconds, SECRET);
    }

    private AiModelConfig config(int timeoutSeconds, String apiKey) {
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
        config.setVersion(0L);
        return config;
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
