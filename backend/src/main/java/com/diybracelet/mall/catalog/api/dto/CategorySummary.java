package com.diybracelet.mall.catalog.api.dto;

/**
 * 小程序分类筛选项。
 *
 * @param id 分类主键
 * @param name 展示名称
 * @param slug 稳定英文标识
 */
public record CategorySummary(
        Long id,
        String name,
        String slug
) {
}
