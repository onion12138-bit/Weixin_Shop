package com.diybracelet.mall.catalog.api;

import com.diybracelet.mall.catalog.api.dto.ChangeCatalogStatusRequest;
import com.diybracelet.mall.catalog.api.dto.ChangePackageRecommendationRequest;
import com.diybracelet.mall.catalog.api.dto.CreateBeadSkuRequest;
import com.diybracelet.mall.catalog.api.dto.AdminBeadSkuView;
import com.diybracelet.mall.catalog.api.dto.AdminPackageDetail;
import com.diybracelet.mall.catalog.api.dto.CatalogImportResult;
import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.catalog.api.dto.SaveBeadSkuRequest;
import com.diybracelet.mall.catalog.api.dto.SaveCategoryRequest;
import com.diybracelet.mall.catalog.api.dto.SavePackageRequest;
import com.diybracelet.mall.catalog.application.AdminCatalogService;
import com.diybracelet.mall.catalog.application.CatalogExcelImportService;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.domain.Category;
import com.diybracelet.mall.catalog.domain.ProductPackage;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 管理后台商品目录维护接口。
 *
 * <p>安全配置统一要求 {@code ROLE_ADMIN}。新增返回 201，修改和状态切换返回 200；
 * 所有响应继续使用三端一致的 {@link ApiResponse} 契约。</p>
 */
@Validated
@RestController
@RequestMapping("/api/admin/catalog")
public class AdminCatalogController {

    private final AdminCatalogService adminCatalogService;
    private final CatalogExcelImportService catalogExcelImportService;

    public AdminCatalogController(
            AdminCatalogService adminCatalogService,
            CatalogExcelImportService catalogExcelImportService
    ) {
        this.adminCatalogService = adminCatalogService;
        this.catalogExcelImportService = catalogExcelImportService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> listCategories() {
        return success(adminCatalogService.listCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<Category>> createCategory(
            @Valid @RequestBody SaveCategoryRequest request
    ) {
        return created(adminCatalogService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<Category> updateCategory(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody SaveCategoryRequest request
    ) {
        return success(adminCatalogService.updateCategory(id, request));
    }

    @PatchMapping("/categories/{id}/status")
    public ApiResponse<Category> changeCategoryStatus(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody ChangeCatalogStatusRequest request
    ) {
        return success(adminCatalogService.changeCategoryStatus(
                id,
                request.status()
        ));
    }

    @GetMapping("/beads")
    public ApiResponse<PageResponse<AdminBeadSkuView>> listBeads(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(required = false) @Min(1) Long categoryId,
            @RequestParam(required = false) String attribute,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page 必须大于等于 1")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size 必须大于等于 1")
            @Max(value = 100, message = "size 不能超过 100")
            int size
    ) {
        return success(adminCatalogService.listBeads(
                keyword,
                status,
                categoryId,
                attribute,
                page,
                size
        ));
    }

    @GetMapping("/beads/{id}")
    public ApiResponse<BeadSku> getBead(
            @PathVariable @Min(1) Long id
    ) {
        return success(adminCatalogService.getBead(id));
    }

    @PostMapping("/beads")
    public ResponseEntity<ApiResponse<BeadSku>> createBead(
            @Valid @RequestBody CreateBeadSkuRequest request
    ) {
        return created(adminCatalogService.createBead(request));
    }

    @GetMapping("/beads/import-template")
    public ResponseEntity<byte[]> downloadBeadImportTemplate() {
        byte[] content = catalogExcelImportService.createTemplate();
        String disposition = ContentDisposition.attachment()
                .filename(
                        CatalogExcelImportService.TEMPLATE_FILE_NAME,
                        StandardCharsets.UTF_8
                )
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.sheet"
                ))
                .contentLength(content.length)
                .body(content);
    }

    @PostMapping(
            value = "/beads/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<CatalogImportResult> importBeads(
            @RequestParam("file") MultipartFile file
    ) {
        return success(catalogExcelImportService.importProducts(file));
    }

    @PutMapping("/beads/{id}")
    public ApiResponse<BeadSku> updateBead(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody SaveBeadSkuRequest request
    ) {
        return success(adminCatalogService.updateBead(id, request));
    }

    @PatchMapping("/beads/{id}/status")
    public ApiResponse<BeadSku> changeBeadStatus(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody ChangeCatalogStatusRequest request
    ) {
        return success(adminCatalogService.changeBeadStatus(
                id,
                request.status()
        ));
    }

    @GetMapping("/packages")
    public ApiResponse<PageResponse<ProductPackage>> listPackages(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page 必须大于等于 1")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size 必须大于等于 1")
            @Max(value = 100, message = "size 不能超过 100")
            int size
    ) {
        return success(adminCatalogService.listPackages(
                keyword,
                status,
                page,
                size
        ));
    }

    @GetMapping("/packages/{id}")
    public ApiResponse<AdminPackageDetail> getPackage(
            @PathVariable @Min(1) Long id
    ) {
        return success(adminCatalogService.getPackage(id));
    }

    @PostMapping("/packages")
    public ResponseEntity<ApiResponse<ProductPackage>> createPackage(
            @Valid @RequestBody SavePackageRequest request
    ) {
        return created(adminCatalogService.createPackage(request));
    }

    @PutMapping("/packages/{id}")
    public ApiResponse<ProductPackage> updatePackage(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody SavePackageRequest request
    ) {
        return success(adminCatalogService.updatePackage(id, request));
    }

    @PatchMapping("/packages/{id}/status")
    public ApiResponse<ProductPackage> changePackageStatus(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody ChangeCatalogStatusRequest request
    ) {
        return success(adminCatalogService.changePackageStatus(
                id,
                request.status()
        ));
    }

    @PatchMapping("/packages/{id}/recommendation")
    public ApiResponse<ProductPackage> changePackageRecommendation(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody ChangePackageRecommendationRequest request
    ) {
        return success(adminCatalogService.changePackageRecommendation(
                id,
                request.recommended()
        ));
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }

    private <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(success(data));
    }
}
