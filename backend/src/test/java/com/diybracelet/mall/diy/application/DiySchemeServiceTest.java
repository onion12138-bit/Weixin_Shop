package com.diybracelet.mall.diy.application;

import com.diybracelet.mall.catalog.api.dto.DiyVisualView;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.catalog.visual.query.PublishedBeadVisualQuery;
import com.diybracelet.mall.diy.domain.DiyEvaluation;
import com.diybracelet.mall.diy.domain.DiyScheme;
import com.diybracelet.mall.diy.domain.FitType;
import com.diybracelet.mall.diy.domain.QuoteStatus;
import com.diybracelet.mall.diy.domain.SizeStatus;
import com.diybracelet.mall.diy.infrastructure.mapper.DiySchemeMapper;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import com.diybracelet.mall.identity.domain.CustomerIdentityResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiySchemeServiceTest {

    @Test
    void shouldResolveVisualsForAllListedSchemesInOneBatch() {
        DiySchemeMapper schemeMapper = mock(DiySchemeMapper.class);
        BeadSkuMapper beadSkuMapper = mock(BeadSkuMapper.class);
        AssetFileMapper assetFileMapper = mock(AssetFileMapper.class);
        CustomerIdentityResolver identityResolver = mock(
                CustomerIdentityResolver.class
        );
        DiyPricingService pricingService = mock(DiyPricingService.class);
        PublishedBeadVisualQuery visualQuery = mock(
                PublishedBeadVisualQuery.class
        );
        DiySchemeService service = new DiySchemeService(
                schemeMapper,
                beadSkuMapper,
                assetFileMapper,
                visualQuery,
                identityResolver,
                pricingService,
                new ObjectMapper()
        );
        when(identityResolver.requireCustomerIdentityId()).thenReturn(101L);
        when(schemeMapper.selectOwnedList(101L)).thenReturn(List.of(
                scheme(1L, 2001L, "透明"),
                scheme(2L, 2002L, "紫色")
        ));
        when(pricingService.evaluate(any())).thenReturn(evaluation());
        List<BeadSku> beads = List.of(bead(2001L), bead(2002L));
        when(beadSkuMapper.selectBatchIds(List.of(2001L, 2002L)))
                .thenReturn(beads);
        when(visualQuery.resolve(beads)).thenReturn(Map.of(
                2001L, new DiyVisualView("tray-1", "canvas-1", 1),
                2002L, new DiyVisualView("tray-2", "canvas-2", 1)
        ));

        assertThat(service.list()).hasSize(2);

        verify(beadSkuMapper).selectBatchIds(List.of(2001L, 2002L));
        verify(visualQuery).resolve(beads);
    }

    private DiyScheme scheme(Long id, Long skuId, String color) {
        DiyScheme scheme = new DiyScheme();
        scheme.setId(id);
        scheme.setName("方案 " + id);
        scheme.setWristCircumferenceMm(new BigDecimal("160.00"));
        scheme.setFitType(FitType.STANDARD);
        scheme.setCordOptionId(6001L);
        scheme.setSequenceJson("[" + skuId + "]");
        scheme.setCatalogSnapshotJson("""
                [{"skuId":%d,"skuCode":"SKU-%d","name":"珠子",
                "color":"%s","diameterMm":8.00,"coverUrl":null,"status":"ON_SALE"}]
                """.formatted(skuId, skuId, color));
        scheme.setQuoteStatus(QuoteStatus.VALID);
        scheme.setQuoteFingerprint("fingerprint");
        return scheme;
    }

    private BeadSku bead(Long id) {
        BeadSku bead = new BeadSku();
        bead.setId(id);
        return bead;
    }

    private DiyEvaluation evaluation() {
        return new DiyEvaluation(
                true,
                List.of(),
                Map.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                SizeStatus.FIT,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "fingerprint"
        );
    }
}
