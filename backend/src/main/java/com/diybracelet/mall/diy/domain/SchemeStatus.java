package com.diybracelet.mall.diy.domain;

/**
 * DIY 方案生命周期状态。
 */
public enum SchemeStatus {

    /** 用户可见、可编辑的方案。 */
    ACTIVE,

    /** 用户删除后的软删除状态，用于避免历史引用丢失。 */
    DELETED
}
