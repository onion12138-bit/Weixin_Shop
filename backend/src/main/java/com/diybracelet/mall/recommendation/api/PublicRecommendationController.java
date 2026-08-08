package com.diybracelet.mall.recommendation.api;

import com.diybracelet.mall.recommendation.api.dto.BirthProfileResponse;
import com.diybracelet.mall.recommendation.api.dto.RecommendationStatusResponse;
import com.diybracelet.mall.recommendation.application.RecommendationService;
import com.diybracelet.mall.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 不依赖访客会话的 AI 可用性探针。
 *
 * <p>响应只包含可用状态，不包含模型密钥或连接参数。实际生成推荐仍由
 * {@code /api/app/recommendations} 校验顾客 JWT。</p>
 */
@RestController
@RequestMapping("/api/public/recommendations")
public class PublicRecommendationController {

    private final RecommendationService recommendationService;

    public PublicRecommendationController(
            RecommendationService recommendationService
    ) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/status")
    public ApiResponse<RecommendationStatusResponse> status() {
        return ApiResponse.success(
            recommendationService.status(),
            UUID.randomUUID().toString()
        );
    }

    @GetMapping("/profile")
    public ApiResponse<BirthProfileResponse> profile(
            @RequestParam(required = false) String birthDate
    ) {
        return ApiResponse.success(
            recommendationService.birthProfile(birthDate),
            UUID.randomUUID().toString()
        );
    }
}
