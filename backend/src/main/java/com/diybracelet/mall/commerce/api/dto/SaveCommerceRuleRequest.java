package com.diybracelet.mall.commerce.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 保存最低下单金额、包邮门槛和基础运费。 */
public record SaveCommerceRuleRequest(
        @NotNull @DecimalMin("0.00") BigDecimal minimumOrderAmount,
        @NotNull @DecimalMin("0.00") BigDecimal freeShippingThreshold,
        @NotNull @DecimalMin("0.00") BigDecimal baseShippingFee
) { }
