package com.diybracelet.mall.catalog.visual.api.dto;

import com.diybracelet.mall.catalog.visual.domain.BeadVisualSourceType;
import com.diybracelet.mall.catalog.visual.domain.BeadVisualStatus;

import java.time.LocalDateTime;


/** 单个珠子视觉版本及其素材信息。 */
public record BeadVisualVersionView(
        Long id,
        Integer version,
        BeadVisualSourceType sourceType,
        BeadVisualStatus status,
        Long trayAssetId,
        String trayUrl,
        Long canvasAssetId,
        String canvasUrl,
        String failureCode,
        String failureMessage,
        String createdBy,
        String publishedBy,
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {
}
