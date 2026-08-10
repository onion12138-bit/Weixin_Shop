package com.diybracelet.mall.recommendation.api;

import com.diybracelet.mall.recommendation.api.dto.RecommendationRequest;
import com.diybracelet.mall.recommendation.api.dto.RecommendationResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationStatusResponse;
import com.diybracelet.mall.recommendation.application.RecommendationService;
import com.diybracelet.mall.recommendation.application.RecommendationExecution;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer-JWT-only endpoints for availability and one recommendation. */
@RestController
@RequestMapping("/api/app/recommendations")
public class AppRecommendationController {

    private final RecommendationService recommendationService;

    public AppRecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/status")
    public ApiResponse<RecommendationStatusResponse> status() {
        return success(recommendationService.status());
    }

    @PostMapping
    public ApiResponse<RecommendationResponse> recommend(
            @Valid @RequestBody RecommendationRequest request
    ) {
        RecommendationExecution execution =
            recommendationService.recommendWithTrace(request);
        return ApiResponse.success(execution.response(), execution.traceId());
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }
}
