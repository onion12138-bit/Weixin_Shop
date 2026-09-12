package com.diybracelet.mall.diy.api.dto;

import com.diybracelet.mall.diy.domain.DiyDesignCommand;
import com.diybracelet.mall.diy.domain.FitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 保存或更新 DIY 方案的请求。
 *
 * <p>创建接口会忽略 {@code version} 并从 0 开始；更新接口必须提交当前版本，
 * 由服务端通过乐观锁防止多页面并发覆盖。</p>
 */
public record SaveDiySchemeRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @DecimalMin("0.01") BigDecimal wristCircumferenceMm,
        @NotNull FitType fitType,
        @NotNull @Positive Long cordOptionId,
        @NotNull @Size(max = 200) List<@NotNull @Positive Long> sequence,
        @NotNull @Min(0) Long version
) {

    /**
     * 转为计价服务可复用的设计命令。
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
