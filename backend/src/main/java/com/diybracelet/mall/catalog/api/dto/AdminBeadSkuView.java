package com.diybracelet.mall.catalog.api.dto;

import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.domain.ProductType;
import java.math.BigDecimal;

/**
 * 后台散珠列表与编辑共用的展示模型。
 *
 * <p>后台需要成本、预警阈值等经营字段，同时也需要可直接展示的封面公开地址。
 * 素材地址由服务端从 {@code coverAssetId} 批量解析，前端不能用素材主键猜测路径。</p>
 */
public record AdminBeadSkuView(
        Long id,
        Long categoryId,
        ProductType productType,
        String skuCode,
        String name,
        String material,
        String color,
        BigDecimal diameterMm,
        BigDecimal salePrice,
        BigDecimal costPrice,
        Integer stockAlertThreshold,
        Integer availableQty,
        Integer reservedQty,
        Integer soldQty,
        StockWarningStatus stockWarningStatus,
        Long coverAssetId,
        String coverUrl,
        String tagsJson,
        CatalogStatus status
) {
}
