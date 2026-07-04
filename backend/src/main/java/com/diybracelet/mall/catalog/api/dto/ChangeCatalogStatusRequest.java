package com.diybracelet.mall.catalog.api.dto;

import com.diybracelet.mall.catalog.domain.CatalogStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 后台单独修改目录对象状态的请求。
 */
public record ChangeCatalogStatusRequest(
        @NotNull(message = "状态不能为空")
        CatalogStatus status
) {
}
