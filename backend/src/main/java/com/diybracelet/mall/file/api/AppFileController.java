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

/** 顾客头像上传入口。 */
@RestController
@RequestMapping("/api/app/files")
public class AppFileController {
    private final FileStorageService fileStorageService;

    public AppFileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadedFileResponse>> upload(
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        fileStorageService.store(file),
                        UUID.randomUUID().toString()
                )
        );
    }
}
