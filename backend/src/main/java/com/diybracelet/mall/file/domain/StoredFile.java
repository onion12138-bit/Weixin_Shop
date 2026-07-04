package com.diybracelet.mall.file.domain;

/**
 * 已存储文件的不可变元数据。
 *
 * @param storageKey 存储适配器内部的稳定文件键
 * @param originalName 用户上传时的文件名，仅用于展示
 * @param contentType 经过白名单验证的 MIME 类型
 * @param sizeBytes 文件字节数
 * @param publicUrl 当前应用可公开读取文件的相对 URL
 */
public record StoredFile(
        String storageKey,
        String originalName,
        String contentType,
        long sizeBytes,
        String publicUrl
) {
}
