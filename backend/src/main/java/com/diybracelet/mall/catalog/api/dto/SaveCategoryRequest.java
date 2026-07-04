package com.diybracelet.mall.catalog.api.dto;

import com.diybracelet.mall.catalog.domain.CatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 后台新建或修改分类的请求。
 */
public record SaveCategoryRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 80, message = "分类名称不能超过 80 个字符")
        String name,

        @NotBlank(message = "分类 slug 不能为空")
        @Size(max = 80, message = "分类 slug 不能超过 80 个字符")
        @Pattern(
                regexp = "[a-z0-9]+(?:-[a-z0-9]+)*",
                message = "分类 slug 只能包含小写字母、数字和连字符"
        )
        String slug,

        @NotNull(message = "分类排序不能为空")
        @PositiveOrZero(message = "分类排序不能为负数")
        Integer sortOrder,

        @NotNull(message = "分类状态不能为空")
        CatalogStatus status
) {
}
