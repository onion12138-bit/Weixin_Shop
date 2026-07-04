package com.diybracelet.mall.catalog.api;

import com.diybracelet.mall.catalog.api.dto.BeadSkuDetail;
import com.diybracelet.mall.catalog.api.dto.BeadSkuSummary;
import com.diybracelet.mall.catalog.api.dto.CategorySummary;
import com.diybracelet.mall.catalog.api.dto.PackageDetail;
import com.diybracelet.mall.catalog.api.dto.PackageSummary;
import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.catalog.application.CatalogQueryService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 微信小程序使用的公开商品目录接口。
 */
@Validated
@RestController
@RequestMapping("/api/public/catalog")
public class PublicCatalogController {

    private final CatalogQueryService catalogQueryService;

    public PublicCatalogController(
            CatalogQueryService catalogQueryService
    ) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategorySummary>> listCategories() {
        return success(catalogQueryService.listCategories());
    }

    @GetMapping("/beads")
    public ApiResponse<PageResponse<BeadSkuSummary>> listBeads(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page 必须大于等于 1")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size 必须大于等于 1")
            @Max(value = 100, message = "size 不能超过 100")
            int size
    ) {
        return success(catalogQueryService.listBeads(
                categoryId,
                keyword,
                page,
                size
        ));
    }

    @GetMapping("/beads/{id}")
    public ApiResponse<BeadSkuDetail> getBead(
            @PathVariable
            @Min(value = 1, message = "散珠主键必须大于零")
            Long id
    ) {
        return success(catalogQueryService.getBead(id));
    }

    @GetMapping("/packages")
    public ApiResponse<PageResponse<PackageSummary>> listPackages(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page 必须大于等于 1")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size 必须大于等于 1")
            @Max(value = 100, message = "size 不能超过 100")
            int size
    ) {
        return success(catalogQueryService.listPackages(page, size));
    }

    @GetMapping("/packages/{id}")
    public ApiResponse<PackageDetail> getPackage(
            @PathVariable
            @Min(value = 1, message = "套餐主键必须大于零")
            Long id
    ) {
        return success(catalogQueryService.getPackage(id));
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }
}
