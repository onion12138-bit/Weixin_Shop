package com.diybracelet.mall.customer.domain;

/** 发券意图的持久结果，和券记录在同一事务提交。 */
public record CouponGrantBatch(String batchId, String requestHash, Integer requestedCount,
                               Integer grantedCount, Integer skippedCount, Long couponId) { }
