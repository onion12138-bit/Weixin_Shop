package com.diybracelet.mall.diy.domain;

/**
 * DIY 方案报价快照相对当前配置的状态。
 */
public enum QuoteStatus {

    /** 尚未产生过报价；当前版本暂不主动使用，预留给草稿流。 */
    NOT_QUOTED,

    /** 已保存报价与当前重新计算结果一致。 */
    VALID,

    /** 当前仍可报价，但价格、规则或绳材版本已不同，需要用户确认新价格。 */
    STALE,

    /** 当前设计无法报价，例如 SKU 下架、尺寸超限或工艺费规则缺失。 */
    INVALID
}
