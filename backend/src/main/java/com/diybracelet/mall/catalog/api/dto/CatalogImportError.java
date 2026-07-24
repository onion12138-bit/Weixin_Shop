package com.diybracelet.mall.catalog.api.dto;

/**
 * Excel 商品导入中的单行错误。
 *
 * @param rowNumber Excel 中面向管理员的行号，从 1 开始
 * @param skuCode 当前行 SKU；无法解析时为空字符串
 * @param message 可直接展示的失败原因
 */
public record CatalogImportError(
        int rowNumber,
        String skuCode,
        String message
) {
}
