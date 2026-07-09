package com.diybracelet.mall.diy.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DIY 设计评估结果。
 *
 * @param quotable 当前设计是否可生成权威报价
 * @param issues 用户可修复的问题集合
 * @param skuQuantities 从顺序数组派生的 SKU 数量
 * @param currentLengthMm 当前设计长度，毫米
 * @param targetLengthMm 目标长度，毫米
 * @param deviationMm 当前长度减目标长度，毫米
 * @param sizeStatus 尺寸状态
 * @param beadAmount 珠子金额，人民币元
 * @param craftFee 工艺费，人民币元
 * @param cordFee 绳材费，人民币元
 * @param totalAmount 总金额，人民币元
 * @param quoteFingerprint 报价指纹；不可报价时为 {@code null}
 */
public record DiyEvaluation(
        boolean quotable,
        List<DiyValidationIssue> issues,
        Map<Long, Integer> skuQuantities,
        BigDecimal currentLengthMm,
        BigDecimal targetLengthMm,
        BigDecimal deviationMm,
        SizeStatus sizeStatus,
        BigDecimal beadAmount,
        BigDecimal craftFee,
        BigDecimal cordFee,
        BigDecimal totalAmount,
        String quoteFingerprint
) {
}
