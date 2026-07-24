package com.diybracelet.mall.diy.api.dto;

import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.api.dto.DiyVisualView;
import com.diybracelet.mall.diy.domain.FitType;
import com.diybracelet.mall.diy.domain.QuoteStatus;
import com.diybracelet.mall.diy.domain.SizeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 小程序 DIY 方案响应。
 *
 * @param sequence 原始 SKU 顺序数组，用于恢复用户排列
 * @param catalogSnapshot 保存时的商品展示快照，用于下架/改名后的恢复展示
 * @param quoteStatus 当前重新评估后的报价状态
 * @param previewUrl 方案预览图公开地址；未上传或文件不可用时为 {@code null}
 */
public record DiySchemeResponse(
        Long id,
        String name,
        BigDecimal wristCircumferenceMm,
        FitType fitType,
        Long cordOptionId,
        List<Long> sequence,
        Integer beadCount,
        Map<Long, Integer> skuQuantities,
        List<CatalogSnapshotView> catalogSnapshot,
        QuoteStatus quoteStatus,
        BigDecimal currentLengthMm,
        BigDecimal targetLengthMm,
        BigDecimal deviationMm,
        SizeStatus sizeStatus,
        BigDecimal beadAmount,
        BigDecimal craftFee,
        BigDecimal cordFee,
        BigDecimal totalAmount,
        String quoteFingerprint,
        Long previewAssetId,
        String previewUrl,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 保存时的 SKU 展示快照。
     */
    public record CatalogSnapshotView(
            Long skuId,
            String skuCode,
            String name,
            String color,
            BigDecimal diameterMm,
            String coverUrl,
            CatalogStatus status,
            DiyVisualView diyVisual
        ) {

        public CatalogSnapshotView(
                Long skuId,
                String skuCode,
                String name,
                BigDecimal diameterMm,
                String coverUrl,
                CatalogStatus status,
                DiyVisualView diyVisual
        ) {
            this(
                    skuId, skuCode, name, null, diameterMm, coverUrl, status,
                    diyVisual
            );
        }

        public CatalogSnapshotView(
                Long skuId,
                String skuCode,
                String name,
                BigDecimal diameterMm,
                String coverUrl,
                CatalogStatus status
        ) {
            this(
                    skuId, skuCode, name, null, diameterMm, coverUrl, status,
                    null
            );
        }
    }
}
