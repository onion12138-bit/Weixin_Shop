package com.diybracelet.mall.recommendation.api;

import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.recommendation.api.dto.AiRecommendationLogView;
import com.diybracelet.mall.recommendation.application.AiRecommendationLogService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only list, detail, and deletion endpoints for model-call traces. */
@Validated
@RestController
@RequestMapping("/api/admin/ai/logs")
public class AdminAiRecommendationLogController {

    private final AiRecommendationLogService service;

    public AdminAiRecommendationLogController(AiRecommendationLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<AiRecommendationLogView.Summary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return success(service.list(status, keyword, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AiRecommendationLogView.Detail> detail(
            @PathVariable @Min(1) long id
    ) {
        return success(service.detail(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Min(1) long id) {
        service.delete(id);
        return success(null);
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }
}
