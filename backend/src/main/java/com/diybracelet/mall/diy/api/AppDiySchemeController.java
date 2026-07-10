package com.diybracelet.mall.diy.api;

import com.diybracelet.mall.diy.api.dto.DiySchemeResponse;
import com.diybracelet.mall.diy.api.dto.SaveDiySchemeRequest;
import com.diybracelet.mall.diy.application.DiySchemeService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 小程序 DIY 方案保存、恢复、复制和删除接口。
 */
@Validated
@RestController
@RequestMapping("/api/app/diy/schemes")
public class AppDiySchemeController {

    private final DiySchemeService schemeService;

    public AppDiySchemeController(DiySchemeService schemeService) {
        this.schemeService = schemeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DiySchemeResponse>> create(
            @Valid @RequestBody SaveDiySchemeRequest request
    ) {
        return created(schemeService.create(request));
    }

    @GetMapping
    public ApiResponse<List<DiySchemeResponse>> list() {
        return success(schemeService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<DiySchemeResponse> get(
            @PathVariable @Min(1) Long id
    ) {
        return success(schemeService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DiySchemeResponse> update(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody SaveDiySchemeRequest request
    ) {
        return success(schemeService.update(id, request));
    }

    @PostMapping("/{id}/copies")
    public ResponseEntity<ApiResponse<DiySchemeResponse>> copy(
            @PathVariable @Min(1) Long id
    ) {
        return created(schemeService.copy(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable @Min(1) Long id) {
        return success(schemeService.delete(id));
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }

    private <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success(data));
    }
}
