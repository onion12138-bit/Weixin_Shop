package com.diybracelet.mall.file.api;

/**
 * 上传成功后返回给管理后台的素材信息。
 *
 * @param id 可写入商品 {@code coverAssetId} 的素材主键
 * @param storageKey 存储适配器内部键
 * @param originalName 原始文件名
 * @param contentType MIME 类型
 * @param sizeBytes 文件大小
 * @param publicUrl 公开读取地址
 */
public record UploadedFileResponse(
        Long id,
        String storageKey,
        String originalName,
        String contentType,
        long sizeBytes,
        String publicUrl
) {
}
