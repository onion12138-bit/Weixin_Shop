package com.diybracelet.mall.catalog.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.catalog.api.dto.AdminPackageDetail;
import com.diybracelet.mall.catalog.api.dto.AdminPackageItem;
import com.diybracelet.mall.catalog.api.dto.AdminBeadSkuView;
import com.diybracelet.mall.catalog.api.dto.AddInventoryRequest;
import com.diybracelet.mall.catalog.api.dto.CreateBeadSkuRequest;
import com.diybracelet.mall.catalog.api.dto.InventoryAdjustmentView;
import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.catalog.api.dto.SaveBeadSkuRequest;
import com.diybracelet.mall.catalog.api.dto.SaveCategoryRequest;
import com.diybracelet.mall.catalog.api.dto.SavePackageItemRequest;
import com.diybracelet.mall.catalog.api.dto.SavePackageRequest;
import com.diybracelet.mall.catalog.api.dto.StockWarningStatus;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.domain.Category;
import com.diybracelet.mall.catalog.domain.PackageItem;
import com.diybracelet.mall.catalog.domain.ProductType;
import com.diybracelet.mall.catalog.domain.ProductPackage;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.catalog.infrastructure.mapper.CategoryMapper;
import com.diybracelet.mall.catalog.infrastructure.mapper.PackageItemMapper;
import com.diybracelet.mall.catalog.infrastructure.mapper.ProductPackageMapper;
import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import com.diybracelet.mall.inventory.domain.InventoryBalance;
import com.diybracelet.mall.inventory.domain.InventoryLedger;
import com.diybracelet.mall.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.diybracelet.mall.inventory.infrastructure.mapper.InventoryLedgerMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryLedgerMapper inventoryLedgerMapper;
    private final AssetFileMapper assetFileMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminCatalogService(
            CategoryMapper categoryMapper,
            BeadSkuMapper beadSkuMapper,
            ProductPackageMapper productPackageMapper,
            PackageItemMapper packageItemMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryLedgerMapper inventoryLedgerMapper,
            AssetFileMapper assetFileMapper,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.categoryMapper = categoryMapper;
        this.beadSkuMapper = beadSkuMapper;
        this.productPackageMapper = productPackageMapper;
        this.packageItemMapper = packageItemMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryLedgerMapper = inventoryLedgerMapper;
        this.assetFileMapper = assetFileMapper;
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
        ProductType nextProductType = requestedProductType(request);
        if (categoryProductType(category) != nextProductType
                && beadSkuMapper.selectCount(
                        new LambdaQueryWrapper<BeadSku>()
                                .eq(BeadSku::getCategoryId, id)
                ) > 0) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "已有商品的分类不能修改商品类型"
            );
        }
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
    public PageResponse<AdminBeadSkuView> listBeads(
            String keyword,
            CatalogStatus status,
            Long categoryId,
            String attribute,
            int page,
            int size
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedAttribute = normalizeAttribute(attribute);
        LambdaQueryWrapper<BeadSku> query = buildBeadAdminQuery(
                normalizedKeyword,
                status,
                categoryId,
                normalizedAttribute
        );
        long total = beadSkuMapper.selectCount(query);
        long offset = (long) (page - 1) * size;
        query.orderByDesc(BeadSku::getId)
                .last("limit " + size + " offset " + offset);
        List<BeadSku> beads = beadSkuMapper.selectList(query);
        Map<Long, String> coverUrls = loadCoverUrls(
                beads.stream().map(BeadSku::getCoverAssetId).toList()
        );
        Map<Long, Category> categoriesById = loadCategoriesById(beads);
        Map<Long, InventoryBalance> balancesBySkuId =
                loadInventoryBalancesBySkuId(beads);
        return new PageResponse<>(
                beads.stream()
                        .map(bead -> toAdminView(
                                bead,
                                categoriesById.get(bead.getCategoryId()),
                                balancesBySkuId.get(bead.getId()),
                                coverUrls
                        ))
                        .toList(),
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
    public BeadSku createBead(CreateBeadSkuRequest request) {
        SaveBeadSkuRequest beadRequest = request.bead();
        Category category = requireCategory(beadRequest.categoryId());
        validateProductFields(category, beadRequest);
        validateAsset(beadRequest.coverAssetId());
        ensureUniqueSkuCode(beadRequest.skuCode(), null);
        BeadSku bead = new BeadSku();
        applyBead(bead, beadRequest, category);
        beadSkuMapper.insert(bead);
        initializeInventory(bead.getId(), request.initialStock());
        return bead;
    }

    @Transactional
    public BeadSku updateBead(
            Long id,
            SaveBeadSkuRequest request
    ) {
        BeadSku bead = requireBead(id);
        Category category = requireCategory(request.categoryId());
        validateProductFields(category, request);
        validateAsset(request.coverAssetId());
        ensureUniqueSkuCode(request.skuCode(), id);
        applyBead(bead, request, category);
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

    /**
     * 为商品追加可用库存，并保留完整调整流水。
     *
     * <p>先锁定余额行，再计算调整后的数量，避免两个管理员同时入库时丢失更新。
     * 该操作不会修改预占和已售数量。</p>
     */
    @Transactional
    public InventoryAdjustmentView addBeadInventory(
            Long id,
            AddInventoryRequest request
    ) {
        requireBead(id);
        InventoryBalance balance = inventoryBalanceMapper
                .selectBySkuForUpdate(id);
        if (balance == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "商品库存余额不存在，请联系管理员修复"
            );
        }
        long afterAvailable = (long) balance.getAvailableQty()
                + request.quantity();
        if (afterAvailable > Integer.MAX_VALUE) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "新增后库存数量超过系统上限"
            );
        }
        if (inventoryBalanceMapper.addAvailable(id, request.quantity()) != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "新增库存失败，请刷新后重试"
            );
        }

        InventoryLedger ledger = new InventoryLedger();
        ledger.setBeadSkuId(id);
        ledger.setChangeType("ADJUST");
        ledger.setQuantity(request.quantity());
        ledger.setBeforeAvailableQty(balance.getAvailableQty());
        ledger.setAfterAvailableQty((int) afterAvailable);
        ledger.setBeforeReservedQty(balance.getReservedQty());
        ledger.setAfterReservedQty(balance.getReservedQty());
        ledger.setBeforeSoldQty(balance.getSoldQty());
        ledger.setAfterSoldQty(balance.getSoldQty());
        ledger.setNote(request.note() == null || request.note().isBlank()
                ? "后台手动新增库存"
                : request.note().trim());
        inventoryLedgerMapper.insert(ledger);

        return new InventoryAdjustmentView(
                id,
                request.quantity(),
                (int) afterAvailable,
                balance.getReservedQty(),
                balance.getSoldQty()
        );
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
                coverUrl(productPackage.getCoverAssetId()),
                productPackage.getStatus(),
                productPackage.getRecommended(),
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

    @Transactional
    public ProductPackage changePackageRecommendation(
            Long id,
            boolean recommended
    ) {
        ProductPackage productPackage = requirePackage(id);
        productPackage.setRecommended(recommended);
        productPackageMapper.updateById(productPackage);
        return productPackage;
    }

    private void applyCategory(
            Category category,
            SaveCategoryRequest request
    ) {
        category.setName(request.name().trim());
        category.setSlug(request.slug().trim());
        category.setProductType(requestedProductType(request));
        category.setSortOrder(request.sortOrder());
        category.setStatus(request.status());
    }

    private void applyBead(
            BeadSku bead,
            SaveBeadSkuRequest request,
            Category category
    ) {
        bead.setCategoryId(request.categoryId());
        bead.setSkuCode(request.skuCode().trim().toUpperCase(Locale.ROOT));
        bead.setName(request.name().trim());
        bead.setMaterial(request.material().trim());
        bead.setColor(request.color().trim());
        bead.setDiameterMm(
                categoryProductType(category) == ProductType.BEAD
                        ? request.diameterMm()
                        : null
        );
        bead.setSalePrice(request.salePrice());
        bead.setCostPrice(request.costPrice());
        bead.setStockAlertThreshold(request.stockAlertThreshold());
        bead.setCoverAssetId(request.coverAssetId());
        bead.setTagsJson(writeTags(request.tags()));
        bead.setStatus(request.status());
    }

    private void initializeInventory(Long beadSkuId, Integer initialStock) {
        InventoryBalance balance = new InventoryBalance();
        balance.setBeadSkuId(beadSkuId);
        balance.setAvailableQty(initialStock);
        balance.setReservedQty(0);
        balance.setSoldQty(0);
        balance.setVersion(0L);
        inventoryBalanceMapper.insert(balance);

        // 流水 quantity 必须大于零；零库存没有数量变动，只保留余额基线。
        if (initialStock == 0) {
            return;
        }
        InventoryLedger ledger = new InventoryLedger();
        ledger.setBeadSkuId(beadSkuId);
        ledger.setChangeType("INIT");
        ledger.setQuantity(initialStock);
        ledger.setBeforeAvailableQty(0);
        ledger.setAfterAvailableQty(initialStock);
        ledger.setBeforeReservedQty(0);
        ledger.setAfterReservedQty(0);
        ledger.setBeforeSoldQty(0);
        ledger.setAfterSoldQty(0);
        ledger.setNote("后台创建商品初始化");
        inventoryLedgerMapper.insert(ledger);
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
        Map<Long, Category> categoriesById = loadCategoriesById(beads);
        if (beads.stream().anyMatch(bead -> categoryProductType(
                categoriesById.get(bead.getCategoryId())
        ) != ProductType.BEAD)) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "成品手串只能包含珠子商品"
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
            CatalogStatus status,
            Long categoryId,
            String attribute
    ) {
        LambdaQueryWrapper<BeadSku> query = new LambdaQueryWrapper<BeadSku>()
                .eq(status != null, BeadSku::getStatus, status)
                .eq(categoryId != null, BeadSku::getCategoryId, categoryId)
                .and(
                        keyword != null,
                        keywordGroup -> keywordGroup
                                .like(BeadSku::getSkuCode, keyword)
                                .or()
                                .like(BeadSku::getName, keyword)
                                .or()
                                .like(BeadSku::getMaterial, keyword)
                );
        if (attribute != null) {
            for (String token : attribute.split("\\s+")) {
                query.and(group -> group
                                .like(BeadSku::getMaterial, token)
                                .or()
                                .like(BeadSku::getColor, token)
                                .or()
                                .like(BeadSku::getDiameterMm, token)
                                .or()
                                .like(BeadSku::getTagsJson, token)
                );
            }
        }
        return query;
    }

    private AdminBeadSkuView toAdminView(
            BeadSku bead,
            Category category,
            InventoryBalance balance,
            Map<Long, String> coverUrls
    ) {
        int availableQty = balance == null ? 0 : balance.getAvailableQty();
        int reservedQty = balance == null ? 0 : balance.getReservedQty();
        int soldQty = balance == null ? 0 : balance.getSoldQty();
        StockWarningStatus warningStatus =
                availableQty <= bead.getStockAlertThreshold()
                        ? StockWarningStatus.RESTOCK
                        : StockWarningStatus.NORMAL;
        return new AdminBeadSkuView(
                bead.getId(),
                bead.getCategoryId(),
                categoryProductType(category),
                bead.getSkuCode(),
                bead.getName(),
                bead.getMaterial(),
                bead.getColor(),
                bead.getDiameterMm(),
                bead.getSalePrice(),
                bead.getCostPrice(),
                bead.getStockAlertThreshold(),
                availableQty,
                reservedQty,
                soldQty,
                warningStatus,
                bead.getCoverAssetId(),
                coverUrls.get(bead.getCoverAssetId()),
                bead.getTagsJson(),
                bead.getStatus()
        );
    }

    private Map<Long, Category> loadCategoriesById(List<BeadSku> beads) {
        List<Long> categoryIds = beads.stream()
                .map(BeadSku::getCategoryId)
                .distinct()
                .toList();
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(
                        Category::getId,
                        Function.identity()
                ));
    }

    private Map<Long, InventoryBalance> loadInventoryBalancesBySkuId(
            List<BeadSku> beads
    ) {
        List<Long> skuIds = beads.stream()
                .map(BeadSku::getId)
                .toList();
        if (skuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return inventoryBalanceMapper.selectList(
                        new LambdaQueryWrapper<InventoryBalance>()
                                .in(InventoryBalance::getBeadSkuId, skuIds)
                ).stream()
                .collect(Collectors.toMap(
                        InventoryBalance::getBeadSkuId,
                        Function.identity()
                ));
    }

    private void validateProductFields(
            Category category,
            SaveBeadSkuRequest request
    ) {
        if (categoryProductType(category) == ProductType.BEAD
                && request.diameterMm() == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "珠子商品必须填写珠径"
            );
        }
    }

    private ProductType requestedProductType(SaveCategoryRequest request) {
        return request.productType() == null
                ? ProductType.BEAD
                : request.productType();
    }

    private ProductType categoryProductType(Category category) {
        return category == null || category.getProductType() == null
                ? ProductType.BEAD
                : category.getProductType();
    }

    /** 批量解析当前页封面，避免列表按行访问素材表。 */
    private String coverUrl(Long assetId) {
        if (assetId == null) {
            return null;
        }
        return loadCoverUrls(List.of(assetId)).get(assetId);
    }

    private Map<Long, String> loadCoverUrls(List<Long> assetIds) {
        List<Long> distinctIds = assetIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return assetFileMapper.selectBatchIds(distinctIds)
                .stream()
                .filter(asset -> "ACTIVE".equals(asset.getStatus()))
                .collect(Collectors.toMap(
                        AssetFile::getId,
                        AssetFile::getPublicUrl
                ));
    }

    private String normalizeAttribute(String attribute) {
        String normalized = normalizeKeyword(attribute);
        if (normalized == null) {
            return null;
        }
        return normalized
                .replaceAll("(?i)(\\d+(?:\\.\\d+)?)\\s*mm\\b", "$1")
                .trim();
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
