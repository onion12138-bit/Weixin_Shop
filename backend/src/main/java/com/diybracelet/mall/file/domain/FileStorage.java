package com.diybracelet.mall.file.domain;

/**
 * 文件二进制存储端口。
 *
 * <p>应用层只依赖该接口，不感知文件位于本地磁盘、MinIO 或云对象存储。
 * 后续切换存储供应商时只需增加新的基础设施适配器。</p>
 */
public interface FileStorage {

    /**
     * 保存一个文件。
     *
     * @param originalName 上传时的原始文件名
     * @param contentType 标准 MIME 类型
     * @param content 完整文件字节
     * @return 可持久化和返回给客户端的文件元数据
     */
    StoredFile store(String originalName, String contentType, byte[] content);

    /**
     * 按存储键读取文件内容。
     *
     * @param storageKey {@link StoredFile#storageKey()} 返回的稳定键
     * @return 文件字节
     */
    byte[] read(String storageKey);
}
