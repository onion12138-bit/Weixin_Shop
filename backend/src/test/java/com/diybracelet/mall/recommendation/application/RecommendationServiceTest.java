package com.diybracelet.mall.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diybracelet.mall.recommendation.api.dto.BirthProfileResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationRequest;
import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationStatusResponse;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelGateway;
import com.diybracelet.mall.recommendation.domain.ModelRecommendation;
import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.recommendation.domain.RecommendationProfile;
import com.diybracelet.mall.recommendation.domain.RecommendationRepairFeedback;
import com.diybracelet.mall.recommendation.infrastructure.AiModelConfigMapper;
import com.diybracelet.mall.recommendation.infrastructure.RecommendationCandidateMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

/** Birthday derivation, model repair, and inventory authority tests. */
class RecommendationServiceTest {

    private AiModelConfigMapper configMapper;
    private RecommendationCandidateMapper candidateMapper;
    private AiModelGateway gateway;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        configMapper = mock(AiModelConfigMapper.class);
        candidateMapper = mock(RecommendationCandidateMapper.class);
        gateway = mock(AiModelGateway.class);
        service = new RecommendationService(configMapper, candidateMapper, gateway);
    }

    @Test
    void shouldExposeOnlyConfiguredAndEnabledAiAsAvailable() {
        when(configMapper.selectById(1L)).thenReturn(
            null,
            config(false, true, true, 2),
            config(true, true, false, 2),
            config(true, true, true, 0),
            config(true, false, true, 2),
            config(true, true, true, 2)
        );

        assertThat(service.status()).isEqualTo(
            new RecommendationStatusResponse("NOT_CONFIGURED", false)
        );
        assertThat(service.status()).isEqualTo(
            new RecommendationStatusResponse("NOT_CONFIGURED", false)
        );
        assertThat(service.status()).isEqualTo(
            new RecommendationStatusResponse("DISABLED", false)
        );
        assertThat(service.status()).isEqualTo(
            new RecommendationStatusResponse("NOT_CONFIGURED", false)
        );
        assertThat(service.status()).isEqualTo(
            new RecommendationStatusResponse("NOT_CONFIGURED", false)
        );
        assertThat(service.status()).isEqualTo(
            new RecommendationStatusResponse("AVAILABLE", true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("birthdayProfiles")
    void shouldDeriveAuthoritativeBirthdayProfile(
            String scenario,
            String birthDate,
            String constellation,
            String zodiac,
            String element
    ) {
        assertThat(service.birthProfile(birthDate)).isEqualTo(
            new BirthProfileResponse(birthDate, constellation, zodiac, element)
        );
    }

    @ParameterizedTest(name = "invalid birthday: {0}")
    @MethodSource("invalidBirthdays")
    void shouldReturnPreciseBirthdayErrors(String raw, ErrorCode expectedCode) {
        assertThatThrownBy(() -> service.birthProfile(raw))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(expectedCode);
    }

    @ParameterizedTest(name = "unavailable config: {0}")
    @MethodSource("unavailableConfigurations")
    void shouldFailBeforeCatalogOrModelWhenConfigurationIsUnavailable(
            String scenario,
            AiModelConfig config,
            ErrorCode expectedCode
    ) {
        when(configMapper.selectById(1L)).thenReturn(config);

        assertThatThrownBy(() -> service.recommend(request()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(expectedCode);

        verify(candidateMapper, never()).selectSellableCandidates();
        verify(gateway, never()).recommend(any(), any(), any(), any());
    }

    @Test
    void shouldReturnCatalogEmptyWithoutCallingTheModel() {
        when(configMapper.selectById(1L)).thenReturn(configured());
        when(candidateMapper.selectSellableCandidates()).thenReturn(List.of());

        assertThatThrownBy(() -> service.recommend(request()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.CATALOG_EMPTY);

        verify(gateway, never()).recommend(any(), any(), any(), any());
    }

    @Test
    void shouldRejectAnUnsupportedWishInspirationBeforeCallingTheModel() {
        when(configMapper.selectById(1L)).thenReturn(configured());

        assertThatThrownBy(() -> service.recommend(
            new RecommendationRequest("1992-01-20", "未知心愿", "专注")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.COMMON_BAD_REQUEST);

        verify(candidateMapper, never()).selectSellableCandidates();
        verify(gateway, never()).recommend(any(), any(), any(), any());
    }

    @Test
    void shouldReturnValidatedBirthdayProfilePlansAndCurrentCatalogImages() {
        prepareSuccess();
        RecommendationRequest request = new RecommendationRequest(
            " 1992-01-20 ", " 事业 ", "  希望日常专注  "
        );

        RecommendationResponse response = service.recommend(request);

        assertThat(response.profile()).isEqualTo(
            new BirthProfileResponse("1992-01-20", "水瓶座", "猴", "风象")
        );
        assertThat(response.plans()).hasSize(2);
        assertThat(response.plans()).extracting(RecommendationResponse.Plan::schemeName)
            .containsExactly("清透通勤", "沉静层次");
        assertThat(response.plans().get(0).items())
            .extracting(RecommendationResponse.Item::imageUrl)
            .containsExactly(
                "/current/2001.png", "/current/2002.png", "/current/2003.png"
            );
        assertThat(response.plans().get(0).items())
            .extracting(RecommendationResponse.Item::reason)
            .containsExactly("清透质感", "柔和层次", "日常点缀");
        assertThat(response.culturalDisclaimer()).contains("文化娱乐与搭配参考");

        ArgumentCaptor<RecommendationProfile> profileCaptor =
            ArgumentCaptor.forClass(RecommendationProfile.class);
        verify(gateway).recommend(
            any(), profileCaptor.capture(), any(), isNull()
        );
        RecommendationProfile profile = profileCaptor.getValue();
        assertThat(profile.birthDate()).isEqualTo("1992-01-20");
        assertThat(profile.constellation()).isEqualTo("水瓶座");
        assertThat(profile.zodiac()).isEqualTo("猴");
        assertThat(profile.constellationElement()).isEqualTo("风象");
        assertThat(profile.wishInspiration()).isEqualTo("事业");
        assertThat(profile.wish()).isEqualTo("希望日常专注");
        assertThat(profile.toString())
            .doesNotContain("1992-01-20", "希望日常专注")
            .contains("sensitiveFieldsRedacted=true");
        assertThat(request.toString())
            .doesNotContain("1992-01-20", "希望日常专注")
            .contains("sensitiveFieldsRedacted=true");
    }

    @ParameterizedTest(name = "repairable gateway error: {0}")
    @MethodSource("repairableErrors")
    void shouldRepairOnceWithOnlyTheAllowListedErrorCode(
            ErrorCode firstError
    ) {
        when(configMapper.selectById(1L)).thenReturn(configured());
        when(candidateMapper.selectSellableCandidates()).thenReturn(candidates());
        when(gateway.recommend(
            any(), any(), any(), nullable(RecommendationRepairFeedback.class)
        )).thenThrow(new BusinessException(firstError, "sensitive prior output"))
            .thenReturn(validModel());
        when(candidateMapper.selectCurrentSellableByIds(any()))
            .thenReturn(currentCandidates());

        RecommendationResponse response = service.recommend(request());

        assertThat(response.plans()).hasSize(2);
        ArgumentCaptor<RecommendationRepairFeedback> feedbackCaptor =
            ArgumentCaptor.forClass(RecommendationRepairFeedback.class);
        verify(gateway, times(2)).recommend(
            any(), any(), any(), feedbackCaptor.capture()
        );
        assertThat(feedbackCaptor.getAllValues())
            .containsExactly(null, new RecommendationRepairFeedback(firstError.name()));
    }

    @Test
    void shouldRepairAServiceDetectedPlanCountMismatchOnce() {
        when(configMapper.selectById(1L)).thenReturn(configured());
        when(candidateMapper.selectSellableCandidates()).thenReturn(candidates());
        when(gateway.recommend(
            any(), any(), any(), nullable(RecommendationRepairFeedback.class)
        )).thenReturn(new ModelRecommendation(List.of(plan("only-one"))))
            .thenReturn(validModel());
        when(candidateMapper.selectCurrentSellableByIds(any()))
            .thenReturn(currentCandidates());

        assertThat(service.recommend(request()).plans()).hasSize(2);

        ArgumentCaptor<RecommendationRepairFeedback> feedbackCaptor =
            ArgumentCaptor.forClass(RecommendationRepairFeedback.class);
        verify(gateway, times(2)).recommend(
            any(), any(), any(), feedbackCaptor.capture()
        );
        assertThat(feedbackCaptor.getAllValues().get(1).code())
            .isEqualTo("AI_RESULT_COUNT_INVALID");
    }

    @Test
    void shouldReloadCandidatesBeforeRepairingAnInvalidSkuResult() {
        List<RecommendationCandidate> fresh = List.of(
            candidate(2001L, "/fresh/2001.png"),
            candidate(2002L, "/fresh/2002.png"),
            candidate(2003L, "/fresh/2003.png")
        );
        when(configMapper.selectById(1L)).thenReturn(configured());
        when(candidateMapper.selectSellableCandidates())
            .thenReturn(candidates(), fresh);
        when(gateway.recommend(
            any(), any(), any(), nullable(RecommendationRepairFeedback.class)
        )).thenThrow(new BusinessException(
            ErrorCode.AI_RESULT_SKU_INVALID, "invalid sku"
        )).thenReturn(validModel());
        when(candidateMapper.selectCurrentSellableByIds(any()))
            .thenReturn(fresh);

        service.recommend(request());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecommendationCandidate>> candidateCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(gateway, times(2)).recommend(
            any(), any(), candidateCaptor.capture(), any()
        );
        assertThat(candidateCaptor.getAllValues())
            .containsExactly(candidates(), fresh);
        verify(candidateMapper, times(2)).selectSellableCandidates();
    }

    @ParameterizedTest(name = "non-repairable: {0}")
    @MethodSource("nonRepairableErrors")
    void shouldNeverRetryNonRepairableProviderOrConfigurationErrors(
            ErrorCode errorCode
    ) {
        when(configMapper.selectById(1L)).thenReturn(configured());
        when(candidateMapper.selectSellableCandidates()).thenReturn(candidates());
        when(gateway.recommend(any(), any(), any(), any())).thenThrow(
            new BusinessException(errorCode, "provider failure")
        );

        assertThatThrownBy(() -> service.recommend(request()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(errorCode);

        verify(gateway).recommend(any(), any(), any(), any());
    }

    @Test
    void shouldStopAfterOneRepairAndPreserveTheSecondDistinctError() {
        when(configMapper.selectById(1L)).thenReturn(configured());
        when(candidateMapper.selectSellableCandidates()).thenReturn(candidates());
        when(gateway.recommend(
            any(), any(), any(), nullable(RecommendationRepairFeedback.class)
        )).thenThrow(new BusinessException(
            ErrorCode.AI_RESPONSE_FORMAT_INVALID, "first"
        )).thenThrow(new BusinessException(
            ErrorCode.AI_RESPONSE_CONTENT_UNSAFE, "second"
        ));

        assertThatThrownBy(() -> service.recommend(request()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.AI_RESPONSE_CONTENT_UNSAFE);

        verify(gateway, times(2)).recommend(any(), any(), any(), any());
    }

    @Test
    void shouldRejectUnsafeCopyAndNeverReturnPartialPlans() {
        when(configMapper.selectById(1L)).thenReturn(configured());
        when(candidateMapper.selectSellableCandidates()).thenReturn(candidates());
        ModelRecommendation unsafe = new ModelRecommendation(List.of(
            new ModelRecommendation.Plan(
                "佩戴后会发财",
                "日常配色。",
                validItems()
            ),
            plan("第二套")
        ));
        when(gateway.recommend(any(), any(), any(), any()))
            .thenReturn(unsafe, unsafe);

        assertThatThrownBy(() -> service.recommend(request()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.AI_RESPONSE_CONTENT_UNSAFE);

        verify(candidateMapper, never()).selectCurrentSellableByIds(any());
        verify(gateway, times(2)).recommend(any(), any(), any(), any());
    }

    @Test
    void shouldTreatDuplicateCurrentCatalogRowsAsInternalDataFailure() {
        when(configMapper.selectById(1L)).thenReturn(configured());
        when(candidateMapper.selectSellableCandidates()).thenReturn(candidates());
        when(gateway.recommend(any(), any(), any(), any())).thenReturn(validModel());
        when(candidateMapper.selectCurrentSellableByIds(any())).thenReturn(List.of(
            candidate(2001L, "/current/a.png"),
            candidate(2001L, "/current/b.png"),
            candidate(2002L, "/current/2002.png"),
            candidate(2003L, "/current/2003.png")
        ));

        assertThatThrownBy(() -> service.recommend(request()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.COMMON_INTERNAL_ERROR);

        verify(gateway).recommend(any(), any(), any(), any());
    }

    private void prepareSuccess() {
        when(configMapper.selectById(1L)).thenReturn(configured());
        when(candidateMapper.selectSellableCandidates()).thenReturn(candidates());
        when(gateway.recommend(any(), any(), any(), any())).thenReturn(validModel());
        when(candidateMapper.selectCurrentSellableByIds(any()))
            .thenReturn(currentCandidates());
    }

    private RecommendationRequest request() {
        return new RecommendationRequest("1992-01-20", null, "专注");
    }

    private AiModelConfig configured() {
        return config(true, true, true, 2);
    }

    private AiModelConfig config(
            boolean hasKey,
            boolean hasPrompt,
            boolean enabled,
            int planCount
    ) {
        AiModelConfig config = new AiModelConfig();
        config.setId(1L);
        config.setProvider("DEEPSEEK");
        config.setBaseUrl("https://api.deepseek.com");
        config.setModelName("deepseek-v4-flash");
        config.setEncryptedApiKey(hasKey ? "encrypted-key" : null);
        config.setEnabled(enabled);
        config.setTimeoutSeconds(30);
        config.setMaxOutputTokens(2048);
        config.setRecommendationPrompt(hasPrompt ? "生成日常材质搭配" : null);
        config.setPlanCount(planCount);
        config.setVersion(1L);
        return config;
    }

    private List<RecommendationCandidate> candidates() {
        return List.of(
            candidate(2001L, "/initial/2001.png"),
            candidate(2002L, "/initial/2002.png"),
            candidate(2003L, "/initial/2003.png")
        );
    }

    private List<RecommendationCandidate> currentCandidates() {
        return List.of(
            candidate(2001L, "/current/2001.png"),
            candidate(2002L, "/current/2002.png"),
            candidate(2003L, "/current/2003.png")
        );
    }

    private RecommendationCandidate candidate(Long id, String imageUrl) {
        return new RecommendationCandidate(
            id,
            "珠子 " + id,
            "天然水晶",
            "灰蓝",
            new java.math.BigDecimal("8.00"),
            new java.math.BigDecimal("10.00"),
            "[\"日常\"]",
            200,
            imageUrl
        );
    }

    private ModelRecommendation validModel() {
        return new ModelRecommendation(List.of(
            new ModelRecommendation.Plan(
                "清透通勤",
                "适合日常的清透材质组合。",
                validItems()
            ),
            new ModelRecommendation.Plan(
                "沉静层次",
                "以沉静色调形成层次。",
                List.of(
                    new ModelRecommendation.Item(2003L, "温和底色"),
                    new ModelRecommendation.Item(2002L, "细腻过渡"),
                    new ModelRecommendation.Item(2001L, "通透收束")
                )
            )
        ));
    }

    private ModelRecommendation.Plan plan(String name) {
        return new ModelRecommendation.Plan(name, "日常材质搭配。", validItems());
    }

    private List<ModelRecommendation.Item> validItems() {
        return List.of(
            new ModelRecommendation.Item(2001L, "清透质感"),
            new ModelRecommendation.Item(2002L, "柔和层次"),
            new ModelRecommendation.Item(2003L, "日常点缀")
        );
    }

    private static Stream<Arguments> birthdayProfiles() {
        return Stream.of(
            Arguments.of("capricorn lower boundary", "2000-01-19", "摩羯座", "龙", "土象"),
            Arguments.of("aquarius boundary", "1992-01-20", "水瓶座", "猴", "风象"),
            Arguments.of("pisces", "2000-02-29", "双鱼座", "龙", "水象"),
            Arguments.of("aries", "2000-03-21", "白羊座", "龙", "火象")
        );
    }

    private static Stream<Arguments> invalidBirthdays() {
        return Stream.of(
            Arguments.of(null, ErrorCode.BIRTH_DATE_REQUIRED),
            Arguments.of("   ", ErrorCode.BIRTH_DATE_REQUIRED),
            Arguments.of("not-a-date", ErrorCode.BIRTH_DATE_INVALID),
            Arguments.of("2023-02-29", ErrorCode.BIRTH_DATE_INVALID),
            Arguments.of(LocalDate.now().plusDays(1).toString(), ErrorCode.BIRTH_DATE_INVALID)
        );
    }

    private static Stream<Arguments> unavailableConfigurations() {
        RecommendationServiceTest fixture = new RecommendationServiceTest();
        return Stream.of(
            Arguments.of("missing singleton", null, ErrorCode.AI_NOT_CONFIGURED),
            Arguments.of(
                "missing key",
                fixture.config(false, true, true, 2),
                ErrorCode.AI_NOT_CONFIGURED
            ),
            Arguments.of(
                "missing prompt",
                fixture.config(true, false, true, 2),
                ErrorCode.AI_PROMPT_NOT_CONFIGURED
            ),
            Arguments.of(
                "invalid plan count",
                fixture.config(true, true, true, 6),
                ErrorCode.AI_PROMPT_NOT_CONFIGURED
            ),
            Arguments.of(
                "disabled",
                fixture.config(true, true, false, 2),
                ErrorCode.AI_DISABLED
            )
        );
    }

    private static Stream<ErrorCode> repairableErrors() {
        return Stream.of(
            ErrorCode.AI_RESPONSE_FORMAT_INVALID,
            ErrorCode.AI_RESULT_COUNT_INVALID,
            ErrorCode.AI_RESULT_ITEM_COUNT_INVALID,
            ErrorCode.AI_RESULT_DUPLICATE_SKU,
            ErrorCode.AI_RESULT_SKU_INVALID,
            ErrorCode.AI_RESPONSE_CONTENT_UNSAFE
        );
    }

    private static Stream<ErrorCode> nonRepairableErrors() {
        return Stream.of(
            ErrorCode.AI_UNAUTHORIZED,
            ErrorCode.AI_MODEL_UNAVAILABLE,
            ErrorCode.AI_RATE_LIMITED,
            ErrorCode.AI_TIMEOUT,
            ErrorCode.AI_UPSTREAM_ERROR
        );
    }
}
