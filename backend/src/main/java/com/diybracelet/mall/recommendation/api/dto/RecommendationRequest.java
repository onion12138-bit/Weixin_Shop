package com.diybracelet.mall.recommendation.api.dto;

import jakarta.validation.constraints.Size;


/** 推荐请求参数；生日由服务层解析以返回明确的校验错误。 */
public record RecommendationRequest(
    String birthDate,
    @Size(max = 8) String wishInspiration,
    @Size(max = 80) String wish
) {
    @Override
    public String toString() {
        return "RecommendationRequest[sensitiveFieldsRedacted=true]";
    }
}
