package com.diybracelet.mall.diy.domain;

/**
 * DIY 可配置选项的启停状态。
 */
public enum OptionStatus {

    /** 当前可被小程序选择和用于权威报价。 */
    ENABLED,

    /** 已停用，历史方案仍可展示，但新方案不能选择。 */
    DISABLED
}
