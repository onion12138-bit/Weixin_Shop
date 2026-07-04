package com.diybracelet.mall.catalog.domain;

/**
 * 商品目录对象的统一生命周期状态。
 *
 * <p>数据库保存枚举名称，接口层也使用相同的稳定英文码。这样前端不依赖数据库实现，
 * 同时分类、散珠 SKU 和成品套餐可以复用同一套上架规则。</p>
 */
public enum CatalogStatus {

    /**
     * 草稿：仅后台可见，内容仍可编辑。
     */
    DRAFT,

    /**
     * 已上架：可以出现在小程序公开目录中。
     */
    ON_SALE,

    /**
     * 已下架：保留历史数据，但不再出现在公开目录中。
     */
    OFF_SALE
}
