package com.diybracelet.mall.catalog.visual.api;

import com.diybracelet.mall.catalog.visual.api.dto.BeadVisualHistoryView;
import com.diybracelet.mall.catalog.visual.api.dto.BeadVisualVersionView;
import com.diybracelet.mall.catalog.visual.api.dto.CreateManualBeadVisualRequest;
import com.diybracelet.mall.catalog.visual.application.BeadVisualService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 管理后台的散珠固定视觉版本接口。 */
@Validated
@RestController
@RequestMapping("/api/admin/catalog/beads/{skuId}/visuals")
public class AdminBeadVisualController {

    private final BeadVisualService beadVisualService;

    public AdminBeadVisualController(BeadVisualService beadVisualService) {
        this.beadVisualService = beadVisualService;
    }

    @PostMapping("/manual")
    public ResponseEntity<ApiResponse<BeadVisualVersionView>> createManual(
            @PathVariable @Min(1) Long skuId,
            @Valid @RequestBody CreateManualBeadVisualRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success(
                beadVisualService.createManual(skuId, request, authentication)
        ));
    }

    @GetMapping
    public ApiResponse<BeadVisualHistoryView> history(
            @PathVariable @Min(1) Long skuId
    ) {
        return success(beadVisualService.history(skuId));
    }

    @PostMapping("/{versionId}/publish")
    public ApiResponse<BeadVisualVersionView> publish(
            @PathVariable @Min(1) Long skuId,
            @PathVariable @Min(1) Long versionId,
            Authentication authentication
    ) {
        return success(beadVisualService.publish(
                skuId,
                versionId,
                authentication
        ));
    }

    @PostMapping("/{versionId}/rollback")
    public ApiResponse<BeadVisualVersionView> rollback(
            @PathVariable @Min(1) Long skuId,
            @PathVariable @Min(1) Long versionId,
            Authentication authentication
    ) {
        return success(beadVisualService.rollback(
                skuId,
                versionId,
                authentication
        ));
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }
}
