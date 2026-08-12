package com.diybracelet.mall.commerce.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceRuleServiceTest {

    private final CommerceRuleService.CheckoutRule rule =
            new CommerceRuleService.CheckoutRule(
                    new BigDecimal("20.00"),
                    new BigDecimal("60.00"),
                    new BigDecimal("8.00")
            );

    @Test
    void shouldChargeBaseFeeBelowPostDiscountFreeShippingThreshold() {
        assertThat(rule.shippingFee(new BigDecimal("59.99")))
                .isEqualByComparingTo("8.00");
    }

    @Test
    void shouldWaiveShippingAtFreeShippingThreshold() {
        assertThat(rule.shippingFee(new BigDecimal("60.00")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
