package com.diybracelet.mall.catalog.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.catalog.api.dto.BeadSkuDetail;
import com.diybracelet.mall.catalog.api.dto.BeadSkuSummary;
import com.diybracelet.mall.catalog.api.dto.CategorySummary;
import com.diybracelet.mall.catalog.api.dto.PackageDetail;
import com.diybracelet.mall.catalog.api.dto.PackageSummary;
import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.domain.PackageItem;
import com.diybracelet.mall.catalog.domain.ProductPackage;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.catalog.infrastructure.mapper.CategoryMapper;
import com.diybracelet.mall.catalog.infrastructure.mapper.PackageItemMapper;
import com.diybracelet.mall.catalog.infrastructure.mapper.ProductPackageMapper;
import com.diybracelet.mall.catalog.visual.query.PublishedBeadVisualQuery;
import com.diybracelet.mall.catalog.api.dto.DiyVisualView;
import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 面向小程序的商品目录只读服务。
 *
 * <p>所有公开查询在服务或 Mapper 层强制附加 {@link CatalogStatus#ON_SALE} 条件，
 * 控制器无法绕过。实体转换为公开 DTO 时会主动剔除成本价和库存预警等经营字段。</p>
 */
@Service
@Transactional(readOnly = true)
public class CatalogQueryService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE =
            new TypeReference<>() {
            };

    private final CategoryMapper categoryMapper;
    private final BeadSkuMapper beadSkuMapper;
    private final ProductPackageMapper productPackageMapper;
    private final PackageItemMapper packageItemMapper;
    private final AssetFileMapper assetFileMapper;
    private final PublishedBeadVisualQuery publishedBeadVisualQuery;
    private final ObjectMapper objectMapper;

    public CatalogQueryService(
            CategoryMapper categoryMapper,
            BeadSkuMapper beadSkuMapper,
            ProductPackageMapper productPackageMapper,
            PackageItemMapper packageItemMapper,
            AssetFileMapper assetFileMapper,
            PublishedBeadVisualQuery publishedBeadVisualQuery,
            ObjectMapper objectMapper
    ) {
        this.categoryMapper = categoryMapper;
        this.beadSkuMapper = beadSkuMapper;
        this.productPackageMapper = productPackageMapper;
        this.packageItemMapper = packageItemMapper;
        this.assetFileMapper = assetFileMapper;
        this.publishedBeadVisualQuery = publishedBeadVisualQuery;
        this.objectMapper = objectMapper;
    }

    /**
     * @return 公开分类，按后台配置顺序排列
     */
    public List<CategorySummary> listCategories() {
        return categoryMapper.selectPublicList()
                .stream()
                .map(category -> new CategorySummary(
                        category.getId(),
                        category.getName(),
                        category.getSlug()
                ))
                .toList();
    }

    /**
     * 分页查询公开散珠。
     */
    public PageResponse<BeadSkuSummary> listBeads(
            Long categoryId,
            String keyword,
            int page,
            int size
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        long offset = (long) (page - 1) * size;
        long total = beadSkuMapper.countPublic(
                categoryId,
                normalizedKeyword
        );
        List<BeadSku> beads = beadSkuMapper.selectPublicPage(
                categoryId,
                normalizedKeyword,
                offset,
                size
        );
        Map<Long, String> coverUrls = loadCoverUrls(
                beads.stream().map(BeadSku::getCoverAssetId).toList()
        );
        Map<Long, DiyVisualView> diyVisuals = publishedBeadVisualQuery
                .resolve(beads);
        List<BeadSkuSummary> items = beads
                .stream()
                .map(bead -> toBeadSummary(bead, coverUrls, diyVisuals))
                .toList();
        return new PageResponse<>(items, total, page, size);
    }

    /**
     * 查询一个已上架散珠详情。
     */
    public BeadSkuDetail getBead(Long id) {
        BeadSku bead = beadSkuMapper.selectPublicById(id);
        if (bead == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "散珠不存在或已下架"
            );
        }
        return new BeadSkuDetail(
                bead.getId(),
                bead.getCategoryId(),
                bead.getSkuCode(),
                bead.getName(),
                bead.getMaterial(),
                bead.getColor(),
                bead.getDiameterMm(),
                bead.getSalePrice(),
                coverUrl(bead.getCoverAssetId()),
                parseTags(bead.getTagsJson()),
                publishedBeadVisualQuery.resolve(List.of(bead)).get(bead.getId())
        );
    }

    /**
     * 分页查询公开成品手串。
     */
    public PageResponse<PackageSummary> listPackages(int page, int size) {
        long offset = (long) (page - 1) * size;
        long total = productPackageMapper.countPublic();
        List<ProductPackage> packages =
                productPackageMapper.selectPublicPage(offset, size);
        Map<Long, String> coverUrls = loadCoverUrls(
                packages.stream()
                        .map(ProductPackage::getCoverAssetId)
                        .toList()
        );
        List<PackageSummary> items = packages
                .stream()
                .map(item -> toPackageSummary(item, coverUrls))
                .toList();
        return new PageResponse<>(items, total, page, size);
    }

    /**
     * 查询首页成品精选推荐位。推荐状态由后台配置，公开端仍强制过滤下架成品。
     */
    public List<PackageSummary> listRecommendedPackages(int limit) {
        List<ProductPackage> packages =
                productPackageMapper.selectRecommended(limit);
        Map<Long, String> coverUrls = loadCoverUrls(
                packages.stream()
                        .map(ProductPackage::getCoverAssetId)
                        .toList()
        );
        return packages.stream()
                .map(item -> toPackageSummary(item, coverUrls))
                .toList();
    }

    /**
     * 查询已上架成品手串及其珠子组成。
     */
    public PackageDetail getPackage(Long id) {
        ProductPackage productPackage = productPackageMapper.selectOne(
                new LambdaQueryWrapper<ProductPackage>()
                        .eq(ProductPackage::getId, id)
                        .eq(
                                ProductPackage::getStatus,
                                CatalogStatus.ON_SALE
                        )
        );
        if (productPackage == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "成品手串不存在或已下架"
            );
        }

        List<PackageItem> packageItems = packageItemMapper.selectList(
                new LambdaQueryWrapper<PackageItem>()
                        .eq(PackageItem::getPackageId, id)
                        .orderByAsc(PackageItem::getSortOrder)
        );
        Map<Long, BeadSku> beadsById = loadBeadsById(packageItems);
        List<Long> coverAssetIds = new ArrayList<>();
        coverAssetIds.add(productPackage.getCoverAssetId());
        beadsById.values()
                .stream()
                .map(BeadSku::getCoverAssetId)
                .forEach(coverAssetIds::add);
        Map<Long, String> coverUrls = loadCoverUrls(coverAssetIds);
        Map<Long, DiyVisualView> diyVisuals = publishedBeadVisualQuery.resolve(
                new ArrayList<>(beadsById.values())
        );
        List<PackageDetail.Item> items = packageItems.stream()
                .map(item -> new PackageDetail.Item(
                        item.getId(),
                        item.getBeadSkuId(),
                        item.getQuantity(),
                        item.getSortOrder(),
                        toBeadSummary(
                                requireBead(beadsById, item),
                                coverUrls,
                                diyVisuals
                        )
                ))
                .toList();

        return new PackageDetail(
                productPackage.getId(),
                productPackage.getPackageCode(),
                productPackage.getName(),
                productPackage.getDescription(),
                productPackage.getSalePrice(),
                coverUrls.get(productPackage.getCoverAssetId()),
                items
        );
    }

    private Map<Long, BeadSku> loadBeadsById(
            List<PackageItem> packageItems
    ) {
        if (packageItems.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> beadIds = packageItems.stream()
                .map(PackageItem::getBeadSkuId)
                .distinct()
                .toList();
        return beadSkuMapper.selectBatchIds(beadIds)
                .stream()
                .collect(Collectors.toMap(
                        BeadSku::getId,
                        Function.identity()
                ));
    }

    private BeadSku requireBead(
            Map<Long, BeadSku> beadsById,
            PackageItem item
    ) {
        BeadSku bead = beadsById.get(item.getBeadSkuId());
        if (bead == null) {
            throw new IllegalStateException(
                    "套餐明细引用了不存在的散珠: " + item.getBeadSkuId()
            );
        }
        return bead;
    }

    private BeadSkuSummary toBeadSummary(
            BeadSku bead,
            Map<Long, String> coverUrls,
            Map<Long, DiyVisualView> diyVisuals
    ) {
        return new BeadSkuSummary(
                bead.getId(),
                bead.getCategoryId(),
                bead.getSkuCode(),
                bead.getName(),
                bead.getMaterial(),
                bead.getColor(),
                bead.getDiameterMm(),
                bead.getSalePrice(),
                coverUrls.get(bead.getCoverAssetId()),
                parseTags(bead.getTagsJson()),
                diyVisuals.get(bead.getId())
        );
    }

    private PackageSummary toPackageSummary(
            ProductPackage productPackage,
            Map<Long, String> coverUrls
    ) {
        return new PackageSummary(
                productPackage.getId(),
                productPackage.getPackageCode(),
                productPackage.getName(),
                productPackage.getDescription(),
                productPackage.getSalePrice(),
                coverUrls.get(productPackage.getCoverAssetId())
        );
    }

    /**
     * 批量解析素材公开地址，避免商品列表出现逐行查询。
     */
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

    private String coverUrl(Long assetId) {
        if (assetId == null) {
            return null;
        }
        AssetFile asset = assetFileMapper.selectById(assetId);
        return asset != null && "ACTIVE".equals(asset.getStatus())
                ? asset.getPublicUrl()
                : null;
    }

    private List<String> parseTags(String tagsJson) {
        try {
            return List.copyOf(objectMapper.readValue(
                    tagsJson,
                    STRING_LIST_TYPE
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "散珠标签不是有效 JSON 数组",
                    exception
            );
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
