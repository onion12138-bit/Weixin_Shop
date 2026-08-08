package com.diybracelet.mall.catalog.api.dto;

import com.diybracelet.mall.catalog.domain.CatalogStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台使用的套餐完整编辑快照。
 *
 * <p>公共套餐详情会过滤内部字段并展开公开商品；管理端需要保留素材主键和有序
 * 明细，以便表单能够无损读取、调整和整体提交。</p>
 *
 * @param id 套餐主键
 * @param packageCode 套餐编码
 * @param name 套餐名称
 * @param description 套餐说明
 * @param salePrice 销售价
 * @param coverAssetId 封面素材主键
 * @param coverUrl 封面素材公开地址
 * @param status 目录状态
 * @param recommended 是否进入小程序首页成品精选
 * @param items 有序散珠明细
 */
public record AdminPackageDetail(
        Long id,
        String packageCode,
        String name,
        String description,
        BigDecimal salePrice,
        Long coverAssetId,
        String coverUrl,
        CatalogStatus status,
        Boolean recommended,
        List<AdminPackageItem> items
) {
}
