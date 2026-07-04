package com.diybracelet.mall.file.api;

import com.diybracelet.mall.file.application.FileStorageService;
import com.diybracelet.mall.shared.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 管理后台文件上传接口。
 *
 * <p>安全模块统一要求管理员 JWT；控制器只负责上传契约和 201 响应语义，
 * 文件类型、大小、路径与元数据一致性由应用服务和存储适配器处理。</p>
 */
@RestController
@RequestMapping("/api/admin/files")
public class AdminFileController {

    private final FileStorageService fileStorageService;

    public AdminFileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * 上传一张商品目录图片。
     *
     * @param file multipart 表单中的 {@code file} 字段
     * @return 已存储文件元数据
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadedFileResponse>> upload(
            @RequestParam("file") MultipartFile file
    ) {
        UploadedFileResponse storedFile = fileStorageService.store(file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        storedFile,
                        UUID.randomUUID().toString()
                ));
    }
}
