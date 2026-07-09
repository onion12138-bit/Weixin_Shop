package com.diybracelet.mall.diy.domain;

/**
 * 当前设计长度相对目标长度的尺寸状态。
 */
public enum SizeStatus {

    /** 当前长度在允许误差内。 */
    FIT,

    /** 当前长度短于目标长度。 */
    TOO_SHORT,

    /** 当前长度长于目标长度。 */
    TOO_LONG
}
