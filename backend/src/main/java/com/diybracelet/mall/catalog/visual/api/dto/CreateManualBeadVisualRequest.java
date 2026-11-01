package com.diybracelet.mall.catalog.visual.api.dto;

import jakarta.validation.constraints.NotNull;


/** 管理员手动配置珠子视觉素材的请求参数。 */
public record CreateManualBeadVisualRequest(
        @NotNull Long trayAssetId,
        @NotNull Long canvasAssetId
) {
}
