package com.diybracelet.mall.catalog.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 后台切换成品首页推荐状态的请求。
 */
public record ChangePackageRecommendationRequest(
        @NotNull(message = "推荐状态不能为空")
        Boolean recommended
) {
}
