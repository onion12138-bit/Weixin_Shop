package com.diybracelet.mall.commerce.api.dto;

import java.math.BigDecimal;

/** 小程序和后台共用的交易规则只读视图。 */
public record CommerceRuleView(
        BigDecimal minimumOrderAmount,
        BigDecimal freeShippingThreshold,
        BigDecimal baseShippingFee,
        Long configVersion
) { }
