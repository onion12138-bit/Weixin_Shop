package com.diybracelet.mall.recommendation.api.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One fully validated recommendation using only current server catalog and prices. */
public record RecommendationResponse(
    String schemeName,
    List<Long> sequence,
    Map<Long, Integer> quantities,
    List<Item> items,
    String summary,
    CordOptionView cordOption,
    BigDecimal beadAmount,
    BigDecimal craftFee,
    BigDecimal cordFee,
    BigDecimal totalAmount,
    String quoteFingerprint,
    String culturalDisclaimer
) {
    public RecommendationResponse {
        sequence = List.copyOf(sequence);
        quantities = Collections.unmodifiableMap(new LinkedHashMap<>(quantities));
        items = List.copyOf(items);
    }

    public record Item(
        Long skuId,
        String name,
        String imageUrl,
        int quantity,
        String reason
    ) {
    }

    public record CordOptionView(Long id, String code, String name, String colorHex) {
    }
}
