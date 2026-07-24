package com.diybracelet.mall.catalog.visual.api.dto;

import com.diybracelet.mall.catalog.visual.domain.BeadVisualSourceType;
import com.diybracelet.mall.catalog.visual.domain.BeadVisualStatus;

import java.time.LocalDateTime;

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
