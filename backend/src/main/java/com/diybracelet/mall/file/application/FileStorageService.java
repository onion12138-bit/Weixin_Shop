package com.diybracelet.mall.file.application;

import com.diybracelet.mall.file.api.UploadedFileResponse;
import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.domain.FileStorage;
import com.diybracelet.mall.file.domain.StoredFile;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传与读取的应用服务。
 *
 * <p>控制器不直接操作磁盘，只把 HTTP 上传对象转换为存储端口需要的字节和元数据。
 * 该隔离让对象存储适配器可以无缝替换当前本地实现。</p>
 */
@Service
public class FileStorageService {

    private final FileStorage fileStorage;
    private final AssetFileMapper assetFileMapper;

    public FileStorageService(
            FileStorage fileStorage,
            AssetFileMapper assetFileMapper
    ) {
        this.fileStorage = fileStorage;
        this.assetFileMapper = assetFileMapper;
    }

    /**
     * 保存管理后台上传的图片。
     *
     * @param file multipart 文件
     * @return 已存储文件元数据
     */
    @Transactional
    public UploadedFileResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "上传文件不能为空"
            );
        }
        try {
            StoredFile stored = fileStorage.store(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
            AssetFile assetFile = new AssetFile();
            assetFile.setStorageKey(stored.storageKey());
            assetFile.setOriginalName(stored.originalName());
            assetFile.setContentType(stored.contentType());
            assetFile.setSizeBytes(stored.sizeBytes());
            assetFile.setPublicUrl(stored.publicUrl());
            assetFile.setStatus("ACTIVE");
            assetFileMapper.insert(assetFile);
            return new UploadedFileResponse(
                    assetFile.getId(),
                    stored.storageKey(),
                    stored.originalName(),
                    stored.contentType(),
                    stored.sizeBytes(),
                    stored.publicUrl()
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    exception.getMessage()
            );
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_INTERNAL_ERROR,
                    "读取上传文件失败"
            );
        }
    }

    /**
     * 读取公开文件。
     *
     * @param storageKey 存储键
     * @return 文件字节
     */
    public byte[] read(String storageKey) {
        return fileStorage.read(storageKey);
    }
}
