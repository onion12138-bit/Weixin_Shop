package com.diybracelet.mall.order.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CheckoutQuoteRequest(@NotNull @Positive Long schemeId, @Positive Long couponId) { }
