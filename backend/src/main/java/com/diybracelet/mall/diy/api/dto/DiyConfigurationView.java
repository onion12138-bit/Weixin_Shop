package com.diybracelet.mall.diy.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 小程序端可读取的 DIY 配置视图。
 *
 * <p>只暴露当前可用的尺寸规则、启用工艺费和启用绳材，不包含后台状态字段，避免小程序
 * 依赖运营管理细节。</p>
 *
 * @param rule 尺寸规则
 * @param craftFeeRules 启用工艺费区间
 * @param cordOptions 启用绳材选项
 * @param visualConfig 固定视图渲染配置
 */
public record DiyConfigurationView(
        Rule rule,
        List<CraftFee> craftFeeRules,
        List<Cord> cordOptions,
        VisualConfig visualConfig
) {

    /**
     * DIY 尺寸规则。
     */
    public record Rule(
            BigDecimal minWristMm,
            BigDecimal maxWristMm,
            BigDecimal snugAllowanceMm,
            BigDecimal standardAllowanceMm,
            BigDecimal looseAllowanceMm,
            BigDecimal toleranceMm,
            Integer maxBeadCount,
            Long configVersion
    ) {
    }

    /**
     * 工艺费区间。
     */
    public record CraftFee(
            Long id,
            Integer minBeadCount,
            Integer maxBeadCount,
            BigDecimal fee,
            Long ruleVersion
    ) {
    }

    /**
     * 小程序可选择的绳材。
     */
    public record Cord(
            Long id,
            String code,
            String name,
            String colorHex,
            BigDecimal extraFee,
            Long optionVersion
    ) {
    }

    /** 固定视图渲染配置。 */
    public record VisualConfig(
            String canvasBackgroundColor,
            BigDecimal braceletShadowOpacity,
            BigDecimal beadShadowOpacity,
            BigDecimal beadOverlapRatio,
            Long configVersion
    ) {
    }
}
