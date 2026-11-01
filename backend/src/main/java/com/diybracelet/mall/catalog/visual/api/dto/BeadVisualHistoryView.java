package com.diybracelet.mall.catalog.visual.api.dto;

import java.util.List;


/** 珠子视觉素材的历史版本列表。 */
public record BeadVisualHistoryView(
        Long publishedVisualVersionId,
        List<BeadVisualVersionView> versions
) {
}
