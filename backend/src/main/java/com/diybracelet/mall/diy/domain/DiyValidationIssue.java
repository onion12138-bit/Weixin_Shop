package com.diybracelet.mall.diy.domain;

/**
 * DIY 设计中用户可修复的问题。
 *
 * <p>这些问题不等同于 HTTP 请求失败。接口仍返回 200，让小程序保留当前排列并引导用户
 * 替换 SKU、调整腕围或补齐长度。</p>
 *
 * @param code 稳定问题码
 * @param message 可读提示
 * @param skuId 与问题相关的 SKU；非 SKU 问题为 {@code null}
 */
public record DiyValidationIssue(
        String code,
        String message,
        Long skuId
) {
}
