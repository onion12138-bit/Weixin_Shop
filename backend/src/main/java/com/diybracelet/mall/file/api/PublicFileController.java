package com.diybracelet.mall.file.api;

import com.diybracelet.mall.file.application.FileStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 小程序和管理后台共用的公开文件读取接口。
 */
@RestController
@RequestMapping("/api/public/files")
public class PublicFileController {

    private final FileStorageService fileStorageService;

    public PublicFileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * 读取包含年月目录的文件键。
     *
     * <p>{@code {*storageKey}} 使用 Spring 6 的多路径段捕获语法，使
     * {@code 2026/07/uuid.png} 可以作为一个完整存储键传入服务层。</p>
     *
     * @param storageKey 文件存储键
     * @return 原始图片字节和可缓存响应头
     */
    @GetMapping("/{*storageKey}")
    public ResponseEntity<byte[]> read(
            @PathVariable String storageKey
    ) {
        String normalizedKey = storageKey.startsWith("/")
                ? storageKey.substring(1)
                : storageKey;
        MediaType mediaType = MediaTypeFactory
                .getMediaType(normalizedKey)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(
                        CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic()
                )
                .body(fileStorageService.read(normalizedKey));
    }
}
