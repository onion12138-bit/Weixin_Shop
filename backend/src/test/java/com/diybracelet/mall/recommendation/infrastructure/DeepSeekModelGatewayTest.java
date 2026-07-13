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
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
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

    private static Stream<Arguments> statusMappings() {
        return Stream.of(
            Arguments.of(401, ErrorCode.AI_UNAUTHORIZED),
            Arguments.of(403, ErrorCode.AI_UNAUTHORIZED),
            Arguments.of(429, ErrorCode.AI_RATE_LIMITED),
            Arguments.of(500, ErrorCode.AI_TEMPORARILY_UNAVAILABLE),
            Arguments.of(503, ErrorCode.AI_TEMPORARILY_UNAVAILABLE)
        );
    }

    private DeepSeekModelGateway gateway(HttpClient client) {
        return new DeepSeekModelGateway(client, new ObjectMapper(), new AiSecretCipher(MASTER_KEY));
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
}
