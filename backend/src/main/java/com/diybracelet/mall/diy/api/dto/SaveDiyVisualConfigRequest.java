package com.diybracelet.mall.diy.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** 管理后台提交的固定视图渲染配置。 */
public record SaveDiyVisualConfigRequest(
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$")
        String canvasBackgroundColor,
        @DecimalMin("0.0") @DecimalMax("0.4")
        BigDecimal braceletShadowOpacity,
        @DecimalMin("0.0") @DecimalMax("0.5")
        BigDecimal beadShadowOpacity,
        @DecimalMin("0.0") @DecimalMax("0.15")
        BigDecimal beadOverlapRatio
) {
}
