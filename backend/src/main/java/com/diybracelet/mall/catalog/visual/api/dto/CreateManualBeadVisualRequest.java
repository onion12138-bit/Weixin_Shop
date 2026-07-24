package com.diybracelet.mall.catalog.visual.api.dto;

import jakarta.validation.constraints.NotNull;

public record CreateManualBeadVisualRequest(
        @NotNull Long trayAssetId,
        @NotNull Long canvasAssetId
) {
}
