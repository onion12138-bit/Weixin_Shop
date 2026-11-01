package com.diybracelet.mall.recommendation.api.dto;

/** 服务端根据生日计算的展示信息。 */
public record BirthProfileResponse(
    String birthDate,
    String constellation,
    String zodiac,
    String constellationElement
) {
}
