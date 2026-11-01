package com.diybracelet.mall.order.api.dto;

import java.math.BigDecimal;

/** 结算金额与所对应的方案版本；客户端必须展示同一版本的设计后再确认。 */
public record CheckoutQuoteResponse(String quoteFingerprint, Long schemeVersion, BigDecimal beadAmount,
        BigDecimal craftFee, BigDecimal cordFee, BigDecimal discountAmount,
        BigDecimal shippingFee, BigDecimal totalAmount) { }
