package com.diybracelet.mall.catalog.api.dto;

/** 提供 DIY 画布所需的珠子视觉素材和显示参数。 */
public record DiyVisualView(
        String trayUrl,
        String canvasUrl,
        Integer version
) {
}
