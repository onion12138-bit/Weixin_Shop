package com.diybracelet.mall.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.diybracelet.mall.diy.application.DiyPricingService;
import com.diybracelet.mall.diy.domain.CordOption;
import com.diybracelet.mall.diy.domain.DiyEvaluation;
import com.diybracelet.mall.diy.domain.FitType;
import com.diybracelet.mall.diy.domain.OptionStatus;
import com.diybracelet.mall.diy.domain.SizeStatus;
import com.diybracelet.mall.diy.infrastructure.mapper.CordOptionMapper;
import com.diybracelet.mall.recommendation.api.dto.RecommendationRequest;
import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationStatusResponse;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelGateway;
import com.diybracelet.mall.recommendation.domain.ModelRecommendation;
import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.recommendation.domain.RecommendationProfile;
import com.diybracelet.mall.recommendation.infrastructure.AiModelConfigMapper;
import com.diybracelet.mall.recommendation.infrastructure.RecommendationCandidateMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

/** Backend authority tests for one ephemeral, fully validated recommendation. */
class RecommendationServiceTest {

    private AiModelConfigMapper configMapper;
    private RecommendationCandidateMapper candidateMapper;
    private AiModelGateway gateway;
    private CordOptionMapper cordOptionMapper;
    private DiyPricingService pricingService;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        configMapper = mock(AiModelConfigMapper.class);
        candidateMapper = mock(RecommendationCandidateMapper.class);
        gateway = mock(AiModelGateway.class);
        cordOptionMapper = mock(CordOptionMapper.class);
        pricingService = mock(DiyPricingService.class);
        service = new RecommendationService(
            configMapper,
            candidateMapper,
            gateway,
            cordOptionMapper,
            pricingService
        );
    }

    @Test
    void shouldExposeNotConfiguredDisabledAndAvailableStatuses() {
        when(configMapper.selectById(1L))
            .thenReturn(null, config(false, true), config(true, false), config(true, true));

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
            new RecommendationStatusResponse("AVAILABLE", true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailableConfigurations")
    void shouldFailExplicitlyWithoutLocalFallback(
            String scenario,
            AiModelConfig config,
            ErrorCode expectedCode
    ) {
        when(configMapper.selectById(1L)).thenReturn(config);

        assertThatThrownBy(() -> service.recommend(request()))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(expectedCode));

        verify(candidateMapper, never()).selectSellableCandidates();
        verify(gateway, never()).recommend(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnOnlyServerValidatedSequenceInventoryImagesCordAndAmounts() {
        prepareConfiguredCandidates();
        RecommendationRequest request = request(
            LocalDate.of(1992, 1, 20),
            "手动星座",
            "手动生肖",
            "水",
            "希望保持专注",
            "通勤",
            "灰蓝",
            new BigDecimal("500.00"),
            new BigDecimal("160.00"),
            FitType.STANDARD,
            "sensitive-free-text"
        );
        when(gateway.recommend(any(), any(), any())).thenReturn(modelResult());
        when(candidateMapper.selectCurrentSellableByIds(List.of(2001L, 2002L)))
            .thenReturn(List.of(currentCandidate(2001L, 2), currentCandidate(2002L, 1)));
        when(cordOptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cord()));
        when(pricingService.evaluate(any())).thenReturn(quotable("314.80"));

        RecommendationResponse response = service.recommend(request);

        assertThat(response.schemeName()).isEqualTo("灰蓝通勤");
        assertThat(response.sequence()).containsExactly(2001L, 2002L, 2001L);
        LinkedHashMap<Long, Integer> expectedQuantities = new LinkedHashMap<>();
        expectedQuantities.put(2001L, 2);
        expectedQuantities.put(2002L, 1);
        assertThat(response.quantities()).containsExactlyEntriesOf(expectedQuantities);
        assertThat(response.quantities().values()).allMatch(quantity -> quantity > 0);
        assertThat(response.items()).extracting(RecommendationResponse.Item::imageUrl)
            .containsExactly("/current/2001.png", "/current/2002.png");
        assertThat(response.items()).extracting(RecommendationResponse.Item::reason)
            .containsExactly("清透层次", "紫色点缀");
        assertThat(response.cordOption().id()).isEqualTo(6001L);
        assertThat(response.beadAmount()).isEqualByComparingTo("300.00");
        assertThat(response.craftFee()).isEqualByComparingTo("10.00");
        assertThat(response.cordFee()).isEqualByComparingTo("4.80");
        assertThat(response.totalAmount()).isEqualByComparingTo("314.80");
        assertThat(response.quoteFingerprint()).isEqualTo("server-fingerprint");
        assertThat(response.culturalDisclaimer()).contains("文化娱乐参考");

        ArgumentCaptor<RecommendationProfile> profileCaptor =
            ArgumentCaptor.forClass(RecommendationProfile.class);
        verify(gateway).recommend(any(), profileCaptor.capture(), any());
        RecommendationProfile profile = profileCaptor.getValue();
        assertThat(profile.constellation()).isEqualTo("手动星座");
        assertThat(profile.zodiac()).isEqualTo("手动生肖");
        assertThat(profile.elementPreference()).isEqualTo("水");
        assertThat(profile.freeText()).isEqualTo("sensitive-free-text");
        assertThat(profile.toString())
            .doesNotContain(
                "1992-01-20", "sensitive-free-text", "birthDate", "freeText"
            )
            .contains("sensitiveFieldsRedacted=true");
        assertThat(request.toString())
            .doesNotContain(
                "1992-01-20", "sensitive-free-text", "birthDate", "freeText"
            )
            .contains("sensitiveFieldsRedacted=true");
    }

    @Test
    void shouldDeriveConstellationAndZodiacButNeverFiveElementsFromBirthDate() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(modelResult());
        when(candidateMapper.selectCurrentSellableByIds(any()))
            .thenReturn(List.of(currentCandidate(2001L, 2), currentCandidate(2002L, 1)));
        when(cordOptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cord()));
        when(pricingService.evaluate(any())).thenReturn(quotable("314.80"));
        RecommendationRequest request = request(
            LocalDate.of(2020, 1, 20),
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("500.00"),
            new BigDecimal("160.00"),
            FitType.STANDARD,
            null
        );

        service.recommend(request);

        ArgumentCaptor<RecommendationProfile> profileCaptor =
            ArgumentCaptor.forClass(RecommendationProfile.class);
        verify(gateway).recommend(any(), profileCaptor.capture(), any());
        assertThat(profileCaptor.getValue().constellation()).isEqualTo("水瓶座");
        assertThat(profileCaptor.getValue().zodiac()).isEqualTo("鼠");
        assertThat(profileCaptor.getValue().elementPreference()).isNull();
    }

    @Test
    void shouldRejectUnknownModelSkuBeforeCurrentStateLookup() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(new ModelRecommendation(
            "未知商品",
            List.of(9999L),
            List.of(new ModelRecommendation.Reason(9999L, "未知")),
            "不应返回"
        ));

        assertInvalidRecommendation(() -> service.recommend(request()));

        verify(candidateMapper, never()).selectCurrentSellableByIds(any());
        verify(pricingService, never()).evaluate(any());
    }

    @Test
    void shouldRejectOffSaleRaceAfterTheModelReturns() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(modelResult());
        when(candidateMapper.selectCurrentSellableByIds(any()))
            .thenReturn(List.of(currentCandidate(2001L, 2)));

        assertInvalidRecommendation(() -> service.recommend(request()));

        verify(pricingService, never()).evaluate(any());
    }

    @Test
    void shouldRejectInventoryThatBecameInsufficientAfterTheModelReturns() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(modelResult());
        when(candidateMapper.selectCurrentSellableByIds(any())).thenReturn(List.of(
            currentCandidate(2001L, 1),
            currentCandidate(2002L, 1)
        ));

        assertInvalidRecommendation(() -> service.recommend(request()));

        verify(pricingService, never()).evaluate(any());
    }

    @Test
    void shouldNormalizeAnyPostModelAuthorityFailureWithoutPartialData() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(modelResult());
        when(candidateMapper.selectCurrentSellableByIds(any()))
            .thenThrow(new IllegalStateException("sensitive database detail"));

        assertThatThrownBy(() -> service.recommend(request()))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AI_INVALID_RECOMMENDATION))
            .hasMessageNotContaining("sensitive database detail");
    }

    @Test
    void shouldRejectEmptySequenceInsteadOfProducingNonPositiveQuantities() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(new ModelRecommendation(
            "空方案",
            List.of(),
            List.of(),
            "不应返回"
        ));

        assertInvalidRecommendation(() -> service.recommend(request()));

        verify(candidateMapper, never()).selectCurrentSellableByIds(any());
        verify(pricingService, never()).evaluate(any());
    }

    @Test
    void shouldRejectReasonsThatDoNotCoverExactlyTheSelectedCandidates() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(new ModelRecommendation(
            "理由缺失",
            List.of(2001L, 2002L),
            List.of(new ModelRecommendation.Reason(2001L, "只有一条")),
            "不应返回"
        ));

        assertInvalidRecommendation(() -> service.recommend(request()));
    }

    @Test
    void shouldRejectNonQuotableSizingAndNeverReturnThePartialEvaluation() {
        prepareValidationThroughPricing();
        when(pricingService.evaluate(any())).thenReturn(nonQuotable());

        assertInvalidRecommendation(() -> service.recommend(request()));
    }

    @Test
    void shouldRejectServerTotalOverBudget() {
        prepareValidationThroughPricing();
        when(pricingService.evaluate(any())).thenReturn(quotable("500.01"));

        assertInvalidRecommendation(() -> service.recommend(request()));
    }

    @Test
    void shouldRejectInventoryThatChangesDuringAuthoritativePricing() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(modelResult());
        when(candidateMapper.selectCurrentSellableByIds(any())).thenReturn(
            List.of(currentCandidate(2001L, 2), currentCandidate(2002L, 1)),
            List.of(currentCandidate(2001L, 1), currentCandidate(2002L, 1))
        );
        when(cordOptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cord()));
        when(pricingService.evaluate(any())).thenReturn(quotable("314.80"));

        assertInvalidRecommendation(() -> service.recommend(request()));
    }

    @ParameterizedTest(name = "unsafe copy: {0}")
    @MethodSource("unsafeModelCopy")
    void shouldRejectDeterministicCulturalOrHealthPromisesFromEveryModelTextField(
            String scenario,
            String schemeName,
            String summary,
            String reason
    ) {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(new ModelRecommendation(
            schemeName,
            List.of(2001L),
            List.of(new ModelRecommendation.Reason(2001L, reason)),
            summary
        ));

        assertInvalidRecommendation(() -> service.recommend(request()));
        verify(candidateMapper, never()).selectCurrentSellableByIds(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAllowNeutralCulturalReferencesAndSymbolicMeanings() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(new ModelRecommendation(
            "传统寓意配色",
            List.of(2001L, 2002L, 2001L),
            List.of(
                new ModelRecommendation.Reason(
                    2001L,
                    "蓝色常被用作宁静的色彩象征。"
                ),
                new ModelRecommendation.Reason(
                    2002L,
                    "紫色的传统寓意可作搭配参考。"
                )
            ),
            "色彩象征与传统寓意内容仅供文化娱乐参考，不构成效果承诺。"
        ));
        when(candidateMapper.selectCurrentSellableByIds(List.of(2001L, 2002L)))
            .thenReturn(List.of(currentCandidate(2001L, 2), currentCandidate(2002L, 1)));
        when(cordOptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cord()));
        when(pricingService.evaluate(any())).thenReturn(quotable("314.80"));

        RecommendationResponse response = service.recommend(request());

        assertThat(response.schemeName()).isEqualTo("传统寓意配色");
        assertThat(response.summary()).contains("文化娱乐参考");
        assertThat(response.items())
            .extracting(RecommendationResponse.Item::reason)
            .containsExactly(
                "蓝色常被用作宁静的色彩象征。",
                "紫色的传统寓意可作搭配参考。"
            );
    }

    @Test
    void shouldRejectRecommendationWhenNoEnabledCordExists() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(modelResult());
        when(candidateMapper.selectCurrentSellableByIds(any()))
            .thenReturn(List.of(currentCandidate(2001L, 2), currentCandidate(2002L, 1)));
        when(cordOptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertInvalidRecommendation(() -> service.recommend(request()));

        verify(pricingService, never()).evaluate(any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("upstreamFailures")
    void shouldPreserveStableUpstreamErrorMapping(
            String scenario,
            ErrorCode errorCode
    ) {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenThrow(
            new BusinessException(errorCode, "AI 服务请求失败")
        );

        assertThatThrownBy(() -> service.recommend(request()))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(errorCode));
    }

    @Test
    void candidateSqlMustJoinCurrentOnSaleSkuAndPositiveInventoryWithoutCost() {
        Select initial = methodSelect("selectSellableCandidates");
        Select current = methodSelect("selectCurrentSellableByIds", List.class);
        String sql = String.join(" ", initial.value()) + " "
            + String.join(" ", current.value());

        assertThat(sql)
            .containsIgnoringCase("join inventory_balance")
            .containsIgnoringCase("status = 'ON_SALE'")
            .containsIgnoringCase("available_qty > 0")
            .containsIgnoringCase("join asset_file")
            .doesNotContainIgnoringCase("left join asset_file")
            .containsIgnoringCase("asset.status = 'ACTIVE'")
            .containsIgnoringCase("asset.public_url is not null")
            .containsIgnoringCase("length(trim(asset.public_url)) > 0")
            .doesNotContainIgnoringCase("cost_price");
    }

    private void prepareConfiguredCandidates() {
        when(configMapper.selectById(1L)).thenReturn(config(true, true));
        when(candidateMapper.selectSellableCandidates()).thenReturn(List.of(
            initialCandidate(2001L, 20),
            initialCandidate(2002L, 20)
        ));
    }

    private void prepareValidationThroughPricing() {
        prepareConfiguredCandidates();
        when(gateway.recommend(any(), any(), any())).thenReturn(modelResult());
        when(candidateMapper.selectCurrentSellableByIds(any()))
            .thenReturn(List.of(currentCandidate(2001L, 2), currentCandidate(2002L, 1)));
        when(cordOptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cord()));
    }

    private void assertInvalidRecommendation(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AI_INVALID_RECOMMENDATION));
    }

    private Select methodSelect(String methodName, Class<?>... parameterTypes) {
        try {
            return RecommendationCandidateMapper.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(Select.class);
        } catch (NoSuchMethodException error) {
            throw new AssertionError(error);
        }
    }

    private RecommendationRequest request() {
        return request(
            null,
            null,
            null,
            "水",
            "专注",
            "通勤",
            "灰蓝",
            new BigDecimal("500.00"),
            new BigDecimal("160.00"),
            FitType.STANDARD,
            null
        );
    }

    private RecommendationRequest request(
            LocalDate birthDate,
            String constellation,
            String zodiac,
            String elementPreference,
            String wish,
            String scene,
            String colorPreference,
            BigDecimal budget,
            BigDecimal wristCircumferenceMm,
            FitType fitType,
            String freeText
    ) {
        return new RecommendationRequest(
            birthDate,
            constellation,
            zodiac,
            elementPreference,
            wish,
            scene,
            colorPreference,
            budget,
            wristCircumferenceMm,
            fitType,
            freeText
        );
    }

    private AiModelConfig config(boolean keyConfigured, boolean enabled) {
        AiModelConfig config = new AiModelConfig();
        config.setId(1L);
        config.setProvider("DEEPSEEK");
        config.setBaseUrl("https://api.deepseek.com");
        config.setModelName("deepseek-v4-flash");
        config.setEncryptedApiKey(keyConfigured ? "encrypted-key" : null);
        config.setEnabled(enabled);
        config.setTimeoutSeconds(15);
        config.setMaxOutputTokens(2048);
        config.setVersion(1L);
        return config;
    }

    private RecommendationCandidate initialCandidate(Long id, int inventory) {
        return new RecommendationCandidate(
            id,
            "珠子 " + id,
            "天然水晶",
            id == 2001L ? "透明" : "紫色",
            new BigDecimal("8.00"),
            new BigDecimal("12.80"),
            "[\"专注\"]",
            inventory,
            "/initial/" + id + ".png"
        );
    }

    private RecommendationCandidate currentCandidate(Long id, int inventory) {
        RecommendationCandidate candidate = initialCandidate(id, inventory);
        return new RecommendationCandidate(
            candidate.skuId(),
            candidate.name(),
            candidate.material(),
            candidate.color(),
            candidate.diameterMm(),
            candidate.salePrice(),
            candidate.tagsJson(),
            candidate.availableQty(),
            "/current/" + id + ".png"
        );
    }

    private ModelRecommendation modelResult() {
        return new ModelRecommendation(
            "灰蓝通勤",
            List.of(2001L, 2002L, 2001L),
            List.of(
                new ModelRecommendation.Reason(2001L, "清透层次"),
                new ModelRecommendation.Reason(2002L, "紫色点缀")
            ),
            "一套适合日常通勤的配色。"
        );
    }

    private CordOption cord() {
        CordOption cord = new CordOption();
        cord.setId(6001L);
        cord.setCode("ELASTIC-CLEAR");
        cord.setName("透明弹力绳");
        cord.setColorHex("#E8E4DC");
        cord.setExtraFee(new BigDecimal("4.80"));
        cord.setStatus(OptionStatus.ENABLED);
        cord.setSortOrder(10);
        cord.setOptionVersion(1L);
        return cord;
    }

    private DiyEvaluation quotable(String total) {
        return new DiyEvaluation(
            true,
            List.of(),
            Map.of(2001L, 2, 2002L, 1),
            new BigDecimal("24.00"),
            new BigDecimal("24.00"),
            BigDecimal.ZERO,
            SizeStatus.FIT,
            new BigDecimal("300.00"),
            new BigDecimal("10.00"),
            new BigDecimal("4.80"),
            new BigDecimal(total),
            "server-fingerprint"
        );
    }

    private DiyEvaluation nonQuotable() {
        return new DiyEvaluation(
            false,
            List.of(),
            Map.of(2001L, 2, 2002L, 1),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            SizeStatus.TOO_SHORT,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null
        );
    }

    private static Stream<Arguments> unavailableConfigurations() {
        RecommendationServiceTest fixture = new RecommendationServiceTest();
        return Stream.of(
            Arguments.of("missing singleton", null, ErrorCode.AI_NOT_CONFIGURED),
            Arguments.of("missing key", fixture.config(false, true), ErrorCode.AI_NOT_CONFIGURED),
            Arguments.of("disabled", fixture.config(true, false), ErrorCode.AI_DISABLED)
        );
    }

    private static Stream<Arguments> upstreamFailures() {
        return Stream.of(
            Arguments.of("unauthorized", ErrorCode.AI_UNAUTHORIZED),
            Arguments.of("model unavailable", ErrorCode.AI_MODEL_UNAVAILABLE),
            Arguments.of("rate limited", ErrorCode.AI_RATE_LIMITED),
            Arguments.of("timeout", ErrorCode.AI_TEMPORARILY_UNAVAILABLE)
        );
    }

    private static Stream<Arguments> unsafeModelCopy() {
        return Stream.of(
            Arguments.of(
                "scheme future fortune outcome",
                "佩戴后会发财",
                "仅作日常配色参考。",
                "灰蓝配色。"
            ),
            Arguments.of(
                "summary cures headache",
                "日常手串",
                "这颗珠子能治好头痛。",
                "灰蓝配色。"
            ),
            Arguments.of(
                "disclaimer cannot sanitize fortune outcome",
                "日常手串",
                "佩戴后会发财，仅供文化娱乐参考。",
                "灰蓝配色。"
            ),
            Arguments.of(
                "reason synonymous fortune outcome",
                "日常手串",
                "仅作日常配色参考。",
                "戴上后就能发大财。"
            ),
            Arguments.of(
                "reason synonymous medical outcome",
                "日常手串",
                "仅作日常配色参考。",
                "这颗珠子可以医好偏头疼。"
            ),
            Arguments.of(
                "scheme guarantee",
                "保证发财手串",
                "仅作日常配色参考。",
                "灰蓝配色。"
            ),
            Arguments.of(
                "summary inevitable outcome",
                "日常手串",
                "佩戴后必然获得财富。",
                "灰蓝配色。"
            ),
            Arguments.of(
                "summary brings luck",
                "日常手串",
                "佩戴后将带来好运。",
                "灰蓝配色。"
            ),
            Arguments.of(
                "summary cancer prevention",
                "日常手串",
                "这套搭配可预防癌症。",
                "灰蓝配色。"
            ),
            Arguments.of(
                "reason disease relief",
                "日常手串",
                "仅作日常配色参考。",
                "这颗珠子能够缓解疾病。"
            ),
            Arguments.of(
                "reason blood pressure",
                "日常手串",
                "仅作日常配色参考。",
                "长期佩戴可降低血压。"
            )
        );
    }
}
