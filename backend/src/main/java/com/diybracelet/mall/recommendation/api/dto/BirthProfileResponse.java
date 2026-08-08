package com.diybracelet.mall.recommendation.api.dto;

/** Server-derived birthday profile displayed before a recommendation is submitted. */
public record BirthProfileResponse(
    String birthDate,
    String constellation,
    String zodiac,
    String constellationElement
) {
}
