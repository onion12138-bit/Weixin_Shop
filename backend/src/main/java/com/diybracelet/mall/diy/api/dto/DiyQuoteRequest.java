package com.diybracelet.mall.diy.api.dto;

import com.diybracelet.mall.diy.domain.DiyDesignCommand;
import com.diybracelet.mall.diy.domain.FitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 小程序请求权威报价的输入契约。
 *
 * <p>客户端只提交用户选择和 SKU 顺序，不提交价格、尺寸结果或任何可被篡改的金额字段。</p>
 *
 * @param wristCircumferenceMm 用户腕围，毫米
 * @param fitType 松紧档位
 * @param cordOptionId 绳材主键
 * @param sequence 有序 SKU ID 数组
 */
public record DiyQuoteRequest(
        @NotNull @DecimalMin("0.01") BigDecimal wristCircumferenceMm,
        @NotNull FitType fitType,
        @NotNull @Positive Long cordOptionId,
        @NotNull @Size(max = 200) List<@NotNull @Positive Long> sequence
) {

    /**
     * 转换为应用服务命令。
     *
     * @return 计价命令
     */
    public DiyDesignCommand toCommand() {
        return new DiyDesignCommand(
                wristCircumferenceMm,
                fitType,
                cordOptionId,
                sequence
        );
    }
}
