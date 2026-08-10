package com.diybracelet.mall.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diybracelet.mall.recommendation.api.dto.BirthProfileResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationRequest;
import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelExchange;
import com.diybracelet.mall.recommendation.domain.AiRecommendationLog;
import com.diybracelet.mall.recommendation.domain.ModelRecommendation;
import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.recommendation.infrastructure.AiRecommendationLogMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiRecommendationLogServiceTest {

    private AiRecommendationLogMapper mapper;
    private AiRecommendationLogService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AiRecommendationLogMapper.class);
        service = new AiRecommendationLogService(mapper, new ObjectMapper());
        when(mapper.insert(any())).thenAnswer(invocation -> {
            AiRecommendationLog log = invocation.getArgument(0);
            log.setId(7L);
            return 1;
        });
    }

    @Test
    void shouldPersistRawInputAndSecretFreeCatalogModelAndPreferenceDiagnostics() {
        long id = service.start(
            "trace-blue-7",
            new RecommendationRequest(
                "2002-08-09", "事业", "我还想要蓝宝石"
            )
        );
        ModelRecommendation model = model();
        RecommendationResponse response = response();

        service.complete(
            id,
            config(),
            candidates(),
            List.of(new AiModelExchange(
                "{\"messages\":[\"safe prompt\"]}",
                200,
                "{\"choices\":[{\"message\":{\"content\":\"safe output\"}}]}",
                88,
                null
            )),
            model,
            response,
            100L
        );

        ArgumentCaptor<AiRecommendationLog> captor =
            ArgumentCaptor.forClass(AiRecommendationLog.class);
        verify(mapper).update(captor.capture());
        AiRecommendationLog log = captor.getValue();
        assertThat(log.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(log.getPreferenceSummary()).isEqualTo("蓝色系");
        assertThat(log.getMatchedCandidateCount()).isEqualTo(2);
        assertThat(log.getMatchedPlanCount()).isEqualTo(1);
        assertThat(log.getCandidateCount()).isEqualTo(3);
        assertThat(log.getAttemptCount()).isEqualTo(1);
        assertThat(log.getCandidatesJson())
            .contains("深蓝金星", "深蓝", "灰蓝")
            .doesNotContain("salePrice", "availableQty", "imageUrl");
        assertThat(log.getConfigSnapshotJson())
            .contains("preferencePrompt", "softPreferenceEnabled")
            .doesNotContain("encryptedApiKey", "provider-secret-ciphertext");
        assertThat(log.getAttemptsJson()).contains("safe prompt", "safe output");
        assertThat(log.getFinalResponseJson()).contains("蓝光启程");
    }

    @Test
    void shouldRejectMissingDeleteTargets() {
        when(mapper.deleteById(99L)).thenReturn(0);

        assertThatThrownBy(() -> service.delete(99L))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.COMMON_NOT_FOUND);
    }

    private AiModelConfig config() {
        AiModelConfig config = new AiModelConfig();
        config.setProvider("DEEPSEEK");
        config.setBaseUrl("https://api.deepseek.com");
        config.setModelName("deepseek-v4-flash");
        config.setEncryptedApiKey("provider-secret-ciphertext");
        config.setEnabled(true);
        config.setTimeoutSeconds(30);
        config.setMaxOutputTokens(2048);
        config.setRecommendationPrompt("生成日常搭配");
        config.setPreferencePrompt("识别颜色和全部标签");
        config.setSoftPreferenceEnabled(true);
        config.setPlanCount(2);
        config.setVersion(3L);
        return config;
    }

    private List<RecommendationCandidate> candidates() {
        return List.of(
            candidate(2030L, "青金石", "深蓝金星", "[\"深蓝\",\"天然石\"]"),
            candidate(2031L, "月光石", "乳白蓝光", "[\"蓝光\"]"),
            candidate(2032L, "拉长石", "灰蓝", "[\"灰色\"]")
        );
    }

    private RecommendationCandidate candidate(
            long id,
            String material,
            String color,
            String tags
    ) {
        return new RecommendationCandidate(
            id,
            "8mm " + material,
            material,
            color,
            new BigDecimal("8.00"),
            new BigDecimal("18.80"),
            tags,
            180,
            "/private/" + id + ".png"
        );
    }

    private ModelRecommendation model() {
        return new ModelRecommendation(
            new ModelRecommendation.PreferenceAnalysis(
                "蓝色系",
                List.of(2030L, 2031L),
                List.of("2030：颜色=深蓝金星", "2031：标签=蓝光")
            ),
            List.of(
                new ModelRecommendation.Plan("蓝光启程", "蓝色点缀", List.of(
                    new ModelRecommendation.Item(2030L, "深蓝层次"),
                    new ModelRecommendation.Item(2032L, "自然过渡"),
                    new ModelRecommendation.Item(2040L, "日常提亮")
                )),
                new ModelRecommendation.Plan("自然焕新", "中性色调", List.of(
                    new ModelRecommendation.Item(2032L, "灰蓝质感"),
                    new ModelRecommendation.Item(2040L, "清透点缀"),
                    new ModelRecommendation.Item(2041L, "柔和收束")
                ))
            )
        );
    }

    private RecommendationResponse response() {
        return new RecommendationResponse(
            new BirthProfileResponse("2002-08-09", "狮子座", "马", "火象"),
            List.of(new RecommendationResponse.Plan(
                "蓝光启程",
                "蓝色点缀",
                List.of(
                    new RecommendationResponse.Item(2030L, "青金石", "/a.png", "深蓝层次")
                )
            )),
            "文化娱乐参考"
        );
    }
}
