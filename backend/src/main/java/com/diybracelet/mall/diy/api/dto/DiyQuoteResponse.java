package com.diybracelet.mall.diy.api.dto;

import com.diybracelet.mall.diy.domain.DiyEvaluation;
import com.diybracelet.mall.diy.domain.DiyValidationIssue;
import com.diybracelet.mall.diy.domain.SizeStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 小程序权威报价响应。
 *
 * @param quotable 当前设计是否可报价
 * @param issues 用户可修复问题集合
 * @param skuQuantities 从顺序数组派生的 SKU 数量
 * @param currentLengthMm 当前长度
 * @param targetLengthMm 目标长度
 * @param deviationMm 长度偏差
 * @param sizeStatus 尺寸状态
 * @param beadAmount 珠子金额
 * @param craftFee 工艺费
 * @param cordFee 绳材费
 * @param totalAmount 总金额
 * @param quoteFingerprint 报价指纹
 */
public record DiyQuoteResponse(
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

    /**
     * 从领域评估结果创建响应。
     *
     * @param evaluation 领域评估结果
     * @return HTTP 响应 DTO
     */
    public static DiyQuoteResponse from(DiyEvaluation evaluation) {
        return new DiyQuoteResponse(
                evaluation.quotable(),
                evaluation.issues(),
                evaluation.skuQuantities(),
                evaluation.currentLengthMm(),
                evaluation.targetLengthMm(),
                evaluation.deviationMm(),
                evaluation.sizeStatus(),
                evaluation.beadAmount(),
                evaluation.craftFee(),
                evaluation.cordFee(),
                evaluation.totalAmount(),
                evaluation.quoteFingerprint()
        );
    }
}
