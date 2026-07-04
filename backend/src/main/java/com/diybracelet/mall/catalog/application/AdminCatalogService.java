package com.diybracelet.mall.catalog.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.catalog.api.dto.AdminPackageDetail;
import com.diybracelet.mall.catalog.api.dto.AdminPackageItem;
import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.catalog.api.dto.SaveBeadSkuRequest;
import com.diybracelet.mall.catalog.api.dto.SaveCategoryRequest;
import com.diybracelet.mall.catalog.api.dto.SavePackageItemRequest;
import com.diybracelet.mall.catalog.api.dto.SavePackageRequest;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.domain.Category;
import com.diybracelet.mall.catalog.domain.PackageItem;
import com.diybracelet.mall.catalog.domain.ProductPackage;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.catalog.infrastructure.mapper.CategoryMapper;
import com.diybracelet.mall.catalog.infrastructure.mapper.PackageItemMapper;
import com.diybracelet.mall.catalog.infrastructure.mapper.ProductPackageMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 管理后台商品目录维护服务。
 *
 * <p>所有写操作在事务内完成。服务在落库前检查业务唯一性和外键可用性，
 * 将数据库约束转换为前端可理解的 404/409 业务语义。套餐更新采用“删除旧明细、
 * 按请求顺序重建”的方式，保证聚合内数据不会出现半新半旧状态。</p>
 */
@Service
public class AdminCatalogService {

    private final CategoryMapper categoryMapper;
    private final BeadSkuMapper beadSkuMapper;
    private final ProductPackageMapper productPackageMapper;
    private final PackageItemMapper packageItemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminCatalogService(
            CategoryMapper categoryMapper,
            BeadSkuMapper beadSkuMapper,
            ProductPackageMapper productPackageMapper,
            PackageItemMapper packageItemMapper,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.categoryMapper = categoryMapper;
        this.beadSkuMapper = beadSkuMapper;
        this.productPackageMapper = productPackageMapper;
        this.packageItemMapper = packageItemMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSortOrder)
                        .orderByAsc(Category::getId)
        );
    }

    @Transactional
    public Category createCategory(SaveCategoryRequest request) {
        ensureUniqueCategorySlug(request.slug(), null);
        Category category = new Category();
        applyCategory(category, request);
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public Category updateCategory(
            Long id,
            SaveCategoryRequest request
    ) {
        Category category = requireCategory(id);
        ensureUniqueCategorySlug(request.slug(), id);
        applyCategory(category, request);
        categoryMapper.updateById(category);
        return category;
    }

    @Transactional
    public Category changeCategoryStatus(
            Long id,
            CatalogStatus status
    ) {
        Category category = requireCategory(id);
        category.setStatus(status);
        categoryMapper.updateById(category);
        return category;
    }

    @Transactional(readOnly = true)
    public PageResponse<BeadSku> listBeads(
            String keyword,
            CatalogStatus status,
            int page,
            int size
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        LambdaQueryWrapper<BeadSku> query = buildBeadAdminQuery(
                normalizedKeyword,
                status
        );
        long total = beadSkuMapper.selectCount(query);
        long offset = (long) (page - 1) * size;
        query.orderByDesc(BeadSku::getId)
                .last("limit " + size + " offset " + offset);
        return new PageResponse<>(
                beadSkuMapper.selectList(query),
                total,
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public BeadSku getBead(Long id) {
        return requireBead(id);
    }

    @Transactional
    public BeadSku createBead(SaveBeadSkuRequest request) {
        requireCategory(request.categoryId());
        validateAsset(request.coverAssetId());
        ensureUniqueSkuCode(request.skuCode(), null);
        BeadSku bead = new BeadSku();
        applyBead(bead, request);
        beadSkuMapper.insert(bead);
        return bead;
    }

    @Transactional
    public BeadSku updateBead(
            Long id,
            SaveBeadSkuRequest request
    ) {
        BeadSku bead = requireBead(id);
        requireCategory(request.categoryId());
        validateAsset(request.coverAssetId());
        ensureUniqueSkuCode(request.skuCode(), id);
        applyBead(bead, request);
        beadSkuMapper.updateById(bead);
        return bead;
    }

    @Transactional
    public BeadSku changeBeadStatus(
            Long id,
            CatalogStatus status
    ) {
        BeadSku bead = requireBead(id);
        bead.setStatus(status);
        beadSkuMapper.updateById(bead);
        return bead;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductPackage> listPackages(
            String keyword,
            CatalogStatus status,
            int page,
            int size
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        LambdaQueryWrapper<ProductPackage> query =
                buildPackageAdminQuery(normalizedKeyword, status);
        long total = productPackageMapper.selectCount(query);
        long offset = (long) (page - 1) * size;
        query.orderByDesc(ProductPackage::getId)
                .last("limit " + size + " offset " + offset);
        return new PageResponse<>(
                productPackageMapper.selectList(query),
                total,
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public AdminPackageDetail getPackage(Long id) {
        ProductPackage productPackage = requirePackage(id);
        List<AdminPackageItem> items = packageItemMapper.selectList(
                        new LambdaQueryWrapper<PackageItem>()
                                .eq(PackageItem::getPackageId, id)
                                .orderByAsc(PackageItem::getSortOrder)
                                .orderByAsc(PackageItem::getId)
                ).stream()
                .map(item -> new AdminPackageItem(
                        item.getBeadSkuId(),
                        item.getQuantity(),
                        item.getSortOrder()
                ))
                .toList();
        return new AdminPackageDetail(
                productPackage.getId(),
                productPackage.getPackageCode(),
                productPackage.getName(),
                productPackage.getDescription(),
                productPackage.getSalePrice(),
                productPackage.getCoverAssetId(),
                productPackage.getStatus(),
                items
        );
    }

    @Transactional
    public ProductPackage createPackage(SavePackageRequest request) {
        validateAsset(request.coverAssetId());
        ensureUniquePackageCode(request.packageCode(), null);
        validatePackageItems(request.items(), request.status());

        ProductPackage productPackage = new ProductPackage();
        applyPackage(productPackage, request);
        productPackageMapper.insert(productPackage);
        replacePackageItems(productPackage.getId(), request.items());
        return productPackage;
    }

    @Transactional
    public ProductPackage updatePackage(
            Long id,
            SavePackageRequest request
    ) {
        ProductPackage productPackage = requirePackage(id);
        validateAsset(request.coverAssetId());
        ensureUniquePackageCode(request.packageCode(), id);
        validatePackageItems(request.items(), request.status());

        applyPackage(productPackage, request);
        productPackageMapper.updateById(productPackage);
        replacePackageItems(id, request.items());
        return productPackage;
    }

    @Transactional
    public ProductPackage changePackageStatus(
            Long id,
            CatalogStatus status
    ) {
        ProductPackage productPackage = requirePackage(id);
        if (status == CatalogStatus.ON_SALE) {
            List<PackageItem> items = packageItemMapper.selectList(
                    new LambdaQueryWrapper<PackageItem>()
                            .eq(PackageItem::getPackageId, id)
            );
            List<SavePackageItemRequest> requests = items.stream()
                    .map(item -> new SavePackageItemRequest(
                            item.getBeadSkuId(),
                            item.getQuantity()
                    ))
                    .toList();
            validatePackageItems(requests, status);
        }
        productPackage.setStatus(status);
        productPackageMapper.updateById(productPackage);
        return productPackage;
    }

    private void applyCategory(
            Category category,
            SaveCategoryRequest request
    ) {
        category.setName(request.name().trim());
        category.setSlug(request.slug().trim());
        category.setSortOrder(request.sortOrder());
        category.setStatus(request.status());
    }

    private void applyBead(
            BeadSku bead,
            SaveBeadSkuRequest request
    ) {
        bead.setCategoryId(request.categoryId());
        bead.setSkuCode(request.skuCode().trim().toUpperCase(Locale.ROOT));
        bead.setName(request.name().trim());
        bead.setMaterial(request.material().trim());
        bead.setColor(request.color().trim());
        bead.setDiameterMm(request.diameterMm());
        bead.setSalePrice(request.salePrice());
        bead.setCostPrice(request.costPrice());
        bead.setStockAlertThreshold(request.stockAlertThreshold());
        bead.setCoverAssetId(request.coverAssetId());
        bead.setTagsJson(writeTags(request.tags()));
        bead.setStatus(request.status());
    }

    private void applyPackage(
            ProductPackage productPackage,
            SavePackageRequest request
    ) {
        productPackage.setPackageCode(
                request.packageCode().trim().toUpperCase(Locale.ROOT)
        );
        productPackage.setName(request.name().trim());
        productPackage.setDescription(request.description().trim());
        productPackage.setSalePrice(request.salePrice());
        productPackage.setCoverAssetId(request.coverAssetId());
        productPackage.setStatus(request.status());
    }

    private void replacePackageItems(
            Long packageId,
            List<SavePackageItemRequest> requests
    ) {
        packageItemMapper.delete(
                new LambdaQueryWrapper<PackageItem>()
                        .eq(PackageItem::getPackageId, packageId)
        );
        for (int index = 0; index < requests.size(); index++) {
            SavePackageItemRequest request = requests.get(index);
            PackageItem item = new PackageItem();
            item.setPackageId(packageId);
            item.setBeadSkuId(request.beadSkuId());
            item.setQuantity(request.quantity());
            item.setSortOrder((index + 1) * 10);
            packageItemMapper.insert(item);
        }
    }

    private void validatePackageItems(
            List<SavePackageItemRequest> requests,
            CatalogStatus packageStatus
    ) {
        Set<Long> beadIds = new HashSet<>();
        requests.forEach(request -> beadIds.add(request.beadSkuId()));
        List<BeadSku> beads = beadSkuMapper.selectBatchIds(beadIds);
        if (beads.size() != beadIds.size()) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "套餐引用的散珠不存在"
            );
        }
        if (packageStatus == CatalogStatus.ON_SALE
                && beads.stream().anyMatch(
                        bead -> bead.getStatus() != CatalogStatus.ON_SALE
                )) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "上架套餐不能包含未上架散珠"
            );
        }
    }

    private Category requireCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "分类不存在"
            );
        }
        return category;
    }

    private BeadSku requireBead(Long id) {
        BeadSku bead = beadSkuMapper.selectById(id);
        if (bead == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "散珠不存在"
            );
        }
        return bead;
    }

    private ProductPackage requirePackage(Long id) {
        ProductPackage productPackage = productPackageMapper.selectById(id);
        if (productPackage == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "成品手串不存在"
            );
        }
        return productPackage;
    }

    private void ensureUniqueCategorySlug(String slug, Long currentId) {
        LambdaQueryWrapper<Category> query =
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getSlug, slug.trim())
                        .ne(currentId != null, Category::getId, currentId);
        if (categoryMapper.selectCount(query) > 0) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "分类 slug 已存在"
            );
        }
    }

    private void ensureUniqueSkuCode(String skuCode, Long currentId) {
        String normalized = skuCode.trim().toUpperCase(Locale.ROOT);
        LambdaQueryWrapper<BeadSku> query =
                new LambdaQueryWrapper<BeadSku>()
                        .eq(BeadSku::getSkuCode, normalized)
                        .ne(currentId != null, BeadSku::getId, currentId);
        if (beadSkuMapper.selectCount(query) > 0) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "SKU 编码已存在"
            );
        }
    }

    private void ensureUniquePackageCode(
            String packageCode,
            Long currentId
    ) {
        String normalized = packageCode
                .trim()
                .toUpperCase(Locale.ROOT);
        LambdaQueryWrapper<ProductPackage> query =
                new LambdaQueryWrapper<ProductPackage>()
                        .eq(ProductPackage::getPackageCode, normalized)
                        .ne(
                                currentId != null,
                                ProductPackage::getId,
                                currentId
                        );
        if (productPackageMapper.selectCount(query) > 0) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "套餐编码已存在"
            );
        }
    }

    private void validateAsset(Long assetId) {
        if (assetId == null) {
            return;
        }
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from asset_file
                where id = ? and status = 'ACTIVE'
                """,
                Long.class,
                assetId
        );
        if (count == null || count == 0) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "封面素材不存在"
            );
        }
    }

    private LambdaQueryWrapper<BeadSku> buildBeadAdminQuery(
            String keyword,
            CatalogStatus status
    ) {
        return new LambdaQueryWrapper<BeadSku>()
                .eq(status != null, BeadSku::getStatus, status)
                .and(
                        keyword != null,
                        query -> query
                                .like(BeadSku::getSkuCode, keyword)
                                .or()
                                .like(BeadSku::getName, keyword)
                                .or()
                                .like(BeadSku::getMaterial, keyword)
                );
    }

    private LambdaQueryWrapper<ProductPackage> buildPackageAdminQuery(
            String keyword,
            CatalogStatus status
    ) {
        return new LambdaQueryWrapper<ProductPackage>()
                .eq(
                        status != null,
                        ProductPackage::getStatus,
                        status
                )
                .and(
                        keyword != null,
                        query -> query
                                .like(
                                        ProductPackage::getPackageCode,
                                        keyword
                                )
                                .or()
                                .like(ProductPackage::getName, keyword)
                );
    }

    private String writeTags(List<String> tags) {
        List<String> normalized = tags.stream()
                .map(String::trim)
                .distinct()
                .toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化散珠标签", exception);
        }
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank()
                ? null
                : keyword.trim();
    }
}
