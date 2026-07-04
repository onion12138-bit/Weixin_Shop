package com.diybracelet.mall.catalog.api.dto;

import java.math.BigDecimal;

/**
 * 小程序成品手串列表项。
 *
 * @param id 套餐主键
 * @param packageCode 稳定套餐编码
 * @param name 展示名称
 * @param description 简介
 * @param salePrice 销售价，单位元
 * @param coverUrl 封面公开地址，可为空
 */
public record PackageSummary(
        Long id,
        String packageCode,
        String name,
        String description,
        BigDecimal salePrice,
        String coverUrl
) {
}
