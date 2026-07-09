package com.diybracelet.mall.diy.domain;

import java.math.BigDecimal;

/**
 * 手串佩戴松紧档位。
 */
public enum FitType {

    /** 贴合佩戴，目标长度使用较小余量。 */
    SNUG,

    /** 标准佩戴，目标长度使用默认余量。 */
    STANDARD,

    /** 宽松佩戴，目标长度使用较大余量。 */
    LOOSE;

    /**
     * 根据当前后台配置返回该松紧档位对应的长度余量。
     *
     * @param config DIY 尺寸规则配置
     * @return 毫米余量
     */
    public BigDecimal allowanceFrom(DiyRuleConfig config) {
        return switch (this) {
            case SNUG -> config.getSnugAllowanceMm();
            case STANDARD -> config.getStandardAllowanceMm();
            case LOOSE -> config.getLooseAllowanceMm();
        };
    }
}
