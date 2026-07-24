package com.diybracelet.mall.diy.api.dto;

import com.diybracelet.mall.diy.domain.OptionStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台使用的 DIY 配置视图。
 *
 * <p>与小程序视图相比，管理端需要看到绳材状态和排序，便于运营人员调整上下架。</p>
 *
 * @param rule 尺寸规则
 * @param craftFeeRules 工艺费区间
 * @param cordOptions 全量绳材选项
 * @param visualConfig 固定视图渲染配置
 */
public record AdminDiyConfigurationView(
        DiyConfigurationView.Rule rule,
        List<DiyConfigurationView.CraftFee> craftFeeRules,
        List<Cord> cordOptions,
        DiyConfigurationView.VisualConfig visualConfig
) {

    /**
     * 管理端绳材视图。
     */
    public record Cord(
            Long id,
            String code,
            String name,
            String colorHex,
            BigDecimal extraFee,
            OptionStatus status,
            Integer sortOrder,
            Long optionVersion
    ) {
    }
}
