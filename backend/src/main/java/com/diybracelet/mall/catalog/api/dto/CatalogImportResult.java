package com.diybracelet.mall.catalog.api.dto;

import java.util.List;

/**
 * Excel 商品批量导入汇总；允许成功行与失败行同时存在。
 */
public record CatalogImportResult(
        int totalRows,
        int successCount,
        int failureCount,
        List<CatalogImportError> errors
) {
}
