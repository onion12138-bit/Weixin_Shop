package com.diybracelet.mall.shared.api;

import com.diybracelet.mall.catalog.api.dto.SavePackageRequest;
import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.BatchGrantCouponRequest;
import com.diybracelet.mall.diy.api.dto.DiyQuoteRequest;
import com.diybracelet.mall.diy.api.dto.SaveCraftFeeRulesRequest;
import com.diybracelet.mall.diy.api.dto.SaveDiySchemeRequest;
import com.diybracelet.mall.diy.domain.FitType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCollectionValidationTest {
    @Test
    void shouldRejectNullItemsBeforeBusinessCodeDereferencesThem() {
        List<Object> requests = List.of(
                new DiyQuoteRequest(new BigDecimal("160"), FitType.STANDARD,
                        6001L, Collections.singletonList(null)),
                new SaveDiySchemeRequest("设计", new BigDecimal("160"),
                        FitType.STANDARD, 6001L, Collections.singletonList(null), 0L),
                new SaveCraftFeeRulesRequest(Collections.singletonList(null)),
                new SavePackageRequest("PACKAGE-1", "手串", "说明", BigDecimal.TEN,
                        null, CatalogStatus.DRAFT, Collections.singletonList(null)),
                new BatchGrantCouponRequest("test-batch-1234567890", "SELECTED", Collections.singletonList(null), null)
        );
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            org.junit.jupiter.api.Assertions.assertAll(requests.stream().map(request -> () -> {
                assertThat(validator.validate(request))
                        .as("null collection item in %s", request.getClass().getSimpleName())
                        .isNotEmpty();
            }));
        }
    }
}
