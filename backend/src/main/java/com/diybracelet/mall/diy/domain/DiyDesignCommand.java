package com.diybracelet.mall.diy.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 进入 DIY 计价服务的纯业务命令。
 *
 * @param wristCircumferenceMm 用户腕围，毫米
 * @param fitType 松紧档位
 * @param cordOptionId 绳材主键
 * @param sequence 有序 SKU ID 数组
 */
public record DiyDesignCommand(
        BigDecimal wristCircumferenceMm,
        FitType fitType,
        Long cordOptionId,
        List<Long> sequence
) {
    public DiyDesignCommand {
        sequence = List.copyOf(sequence);
    }
}
