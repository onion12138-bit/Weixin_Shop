package com.diybracelet.mall.catalog.visual.api.dto;

import java.util.List;

public record BeadVisualHistoryView(
        Long publishedVisualVersionId,
        List<BeadVisualVersionView> versions
) {
}
