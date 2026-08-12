package com.diybracelet.mall.order.application;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNumberFormatTest {

    @Test
    void shouldGenerateOrdNumberWithTimestampAndNumericSuffix() throws Exception {
        Method method = OrderService.class.getDeclaredMethod("generateOrderNo");
        method.setAccessible(true);
        String orderNo = (String) method.invoke(null);
        assertThat(orderNo).matches("ORD\\d{22}");
    }
}
