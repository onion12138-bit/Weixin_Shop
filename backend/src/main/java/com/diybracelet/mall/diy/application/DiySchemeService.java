package com.diybracelet.mall.diy.application;

import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.api.dto.DiyVisualView;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.catalog.visual.query.PublishedBeadVisualQuery;
import com.diybracelet.mall.diy.api.dto.DiySchemeResponse;
import com.diybracelet.mall.diy.api.dto.SaveDiySchemeRequest;
import com.diybracelet.mall.diy.domain.DiyDesignCommand;
import com.diybracelet.mall.diy.domain.DiyEvaluation;
import com.diybracelet.mall.diy.domain.DiyScheme;
import com.diybracelet.mall.diy.domain.QuoteStatus;
import com.diybracelet.mall.diy.domain.SchemeStatus;
import com.diybracelet.mall.diy.infrastructure.mapper.DiySchemeMapper;
import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import com.diybracelet.mall.identity.domain.CustomerIdentityResolver;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 小程序 DIY 方案生命周期服务。
 *
 * <p>服务层同时负责所有权校验、顺序数组持久化、报价快照和读取时的重新评估。
 * Controller 不直接接触 Mapper，避免遗漏顾客维度过滤。</p>
 */
@Service
public class DiySchemeService {

    private static final long MAX_ACTIVE_SCHEMES_PER_CUSTOMER = 3;

    private static final TypeReference<List<Long>> LONG_LIST_TYPE =
            new TypeReference<>() {
            };
    private static final TypeReference<List<CatalogSnapshot>> SNAPSHOT_TYPE =
            new TypeReference<>() {
            };
    private static final String COPY_SUFFIX = " 副本";
    private static final int MAX_NAME_LENGTH = 80;

    private final DiySchemeMapper schemeMapper;
    private final BeadSkuMapper beadSkuMapper;
    private final AssetFileMapper assetFileMapper;
    private final PublishedBeadVisualQuery publishedBeadVisualQuery;
    private final CustomerIdentityResolver identityResolver;
    private final DiyPricingService pricingService;
    private final ObjectMapper objectMapper;

    public DiySchemeService(
            DiySchemeMapper schemeMapper,
            BeadSkuMapper beadSkuMapper,
            AssetFileMapper assetFileMapper,
            PublishedBeadVisualQuery publishedBeadVisualQuery,
            CustomerIdentityResolver identityResolver,
            DiyPricingService pricingService,
            ObjectMapper objectMapper
    ) {
        this.schemeMapper = schemeMapper;
        this.beadSkuMapper = beadSkuMapper;
        this.assetFileMapper = assetFileMapper;
        this.publishedBeadVisualQuery = publishedBeadVisualQuery;
        this.identityResolver = identityResolver;
        this.pricingService = pricingService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建当前顾客的新 DIY 方案。
     */
    @Transactional
    public DiySchemeResponse create(SaveDiySchemeRequest request) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        ensureSchemeCapacity(customerId);
        DiyScheme scheme = toEntity(customerId, request);
        scheme.setVersion(0L);
        schemeMapper.insert(scheme);
        return getOwnedResponse(scheme.getId(), customerId);
    }

    /**
     * 查询当前顾客全部有效方案。
     */
    @Transactional(readOnly = true)
    public List<DiySchemeResponse> list() {
        Long customerId = identityResolver.requireCustomerIdentityId();
        List<DiyScheme> schemes = schemeMapper.selectOwnedList(customerId);
        Map<Long, DiyVisualView> diyVisuals = resolveDiyVisuals(
                schemes.stream()
                        .flatMap(scheme -> parseSnapshots(
                                scheme.getCatalogSnapshotJson()
                        ).stream())
                        .toList()
        );
        return schemes
                .stream()
                .map(scheme -> toResponse(scheme, diyVisuals))
                .toList();
    }

    /**
     * 查询当前顾客拥有的单个方案。
     */
    @Transactional(readOnly = true)
    public DiySchemeResponse get(Long id) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        return getOwnedResponse(id, customerId);
    }

    /**
     * 使用乐观锁更新当前顾客拥有的方案。
     */
    @Transactional
    public DiySchemeResponse update(Long id, SaveDiySchemeRequest request) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        DiyScheme scheme = toEntity(customerId, request);
        scheme.setId(id);
        int updated = schemeMapper.updateOwnedWithVersion(
                scheme,
                customerId,
                request.version()
        );
        if (updated == 0) {
            ensureOwnedSchemeExists(id, customerId);
            throw new BusinessException(
                    ErrorCode.DIY_SCHEME_VERSION_CONFLICT,
                    "方案已在其他页面更新，请重新载入"
            );
        }
        return getOwnedResponse(id, customerId);
    }

    /**
     * 复制当前顾客拥有的方案。
     */
    @Transactional
    public DiySchemeResponse copy(Long id) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        ensureSchemeCapacity(customerId);
        DiyScheme source = requireOwnedScheme(id, customerId);
        DiyScheme copy = new DiyScheme();
        copy.setCustomerIdentityId(customerId);
        copy.setName(copyName(source.getName()));
        copy.setWristCircumferenceMm(source.getWristCircumferenceMm());
        copy.setFitType(source.getFitType());
        copy.setCordOptionId(source.getCordOptionId());
        copy.setSequenceJson(source.getSequenceJson());
        copy.setBeadCount(source.getBeadCount());
        copy.setCatalogSnapshotJson(source.getCatalogSnapshotJson());
        copy.setBeadAmount(source.getBeadAmount());
        copy.setCraftFee(source.getCraftFee());
        copy.setCordFee(source.getCordFee());
        copy.setTotalAmount(source.getTotalAmount());
        copy.setQuoteFingerprint(source.getQuoteFingerprint());
        copy.setQuoteStatus(source.getQuoteStatus());
        copy.setPreviewAssetId(source.getPreviewAssetId());
        copy.setStatus(SchemeStatus.ACTIVE);
        copy.setVersion(0L);
        schemeMapper.insert(copy);
        return getOwnedResponse(copy.getId(), customerId);
    }

    /**
     * 软删除当前顾客拥有的方案。
     */
    @Transactional
    public boolean delete(Long id) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        int updated = schemeMapper.softDeleteOwned(id, customerId);
        if (updated == 0) {
            ensureOwnedSchemeExists(id, customerId);
        }
        return true;
    }

    /**
     * 将已上传的图片素材绑定为当前顾客方案的预览图。
     *
     * <p>绑定失败时不删除已上传文件，避免本地存储和未来对象存储的补偿逻辑不一致。
     * 后续可通过素材清理任务回收未被业务引用的文件。</p>
     */
    @Transactional
    public DiySchemeResponse bindPreview(Long id, Long previewAssetId) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        requireOwnedScheme(id, customerId);
        requireActiveAsset(previewAssetId);
        int updated = schemeMapper.bindPreviewOwned(
                id,
                customerId,
                previewAssetId
        );
        if (updated == 0) {
            ensureOwnedSchemeExists(id, customerId);
        }
        return getOwnedResponse(id, customerId);
    }

    /** 拒绝旧设计、已删除或已下单方案的迟到预览，不影响后续设计保存。 */
    @Transactional
    public DiySchemeResponse bindPreviewVersion(Long id, Long previewAssetId, Long version) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        requireOwnedScheme(id, customerId);
        requireActiveAsset(previewAssetId);
        if (schemeMapper.bindPreviewForVersion(id, customerId, previewAssetId, version) != 1) {
            ensureOwnedSchemeExists(id, customerId);
            throw new BusinessException(
                    ErrorCode.DIY_SCHEME_VERSION_CONFLICT,
                    "方案已更新，无需上传旧预览"
            );
        }
        return getOwnedResponse(id, customerId);
    }

    private DiyScheme toEntity(
            Long customerId,
            SaveDiySchemeRequest request
    ) {
        DiyEvaluation evaluation = pricingService.evaluate(request.toCommand());
        List<CatalogSnapshot> snapshot = snapshotCatalog(request.sequence());
        DiyScheme scheme = new DiyScheme();
        scheme.setCustomerIdentityId(customerId);
        scheme.setName(request.name().trim());
        scheme.setWristCircumferenceMm(request.wristCircumferenceMm());
        scheme.setFitType(request.fitType());
        scheme.setCordOptionId(request.cordOptionId());
        scheme.setSequenceJson(writeJson(request.sequence()));
        scheme.setBeadCount(request.sequence().size());
        scheme.setCatalogSnapshotJson(writeJson(snapshot));
        applyEvaluationSnapshot(scheme, evaluation);
        scheme.setStatus(SchemeStatus.ACTIVE);
        return scheme;
    }

    private void applyEvaluationSnapshot(
            DiyScheme scheme,
            DiyEvaluation evaluation
    ) {
        if (!evaluation.quotable()) {
            scheme.setBeadAmount(null);
            scheme.setCraftFee(null);
            scheme.setCordFee(null);
            scheme.setTotalAmount(null);
            scheme.setQuoteFingerprint(null);
            scheme.setQuoteStatus(QuoteStatus.INVALID);
            return;
        }
        scheme.setBeadAmount(evaluation.beadAmount());
        scheme.setCraftFee(evaluation.craftFee());
        scheme.setCordFee(evaluation.cordFee());
        scheme.setTotalAmount(evaluation.totalAmount());
        scheme.setQuoteFingerprint(evaluation.quoteFingerprint());
        scheme.setQuoteStatus(QuoteStatus.VALID);
    }

    private DiySchemeResponse getOwnedResponse(Long id, Long customerId) {
        return toResponse(requireOwnedScheme(id, customerId));
    }

    private DiyScheme requireOwnedScheme(Long id, Long customerId) {
        DiyScheme scheme = schemeMapper.selectOwnedById(id, customerId);
        if (scheme == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "方案不存在"
            );
        }
        return scheme;
    }

    private void ensureOwnedSchemeExists(Long id, Long customerId) {
        requireOwnedScheme(id, customerId);
    }

    private void ensureSchemeCapacity(Long customerId) {
        // Acquire before any consistent read so MySQL REPEATABLE READ sees the
        // preceding creator's committed rows when it establishes this snapshot.
        schemeMapper.lockCustomerForSchemeChanges(customerId);
        if (schemeMapper.countOwnedActive(customerId)
                < MAX_ACTIVE_SCHEMES_PER_CUSTOMER) {
            return;
        }
        throw new BusinessException(
                ErrorCode.DIY_SCHEME_LIMIT_REACHED,
                "最多保存 3 个 DIY 方案"
        );
    }

    private DiySchemeResponse toResponse(DiyScheme scheme) {
        List<CatalogSnapshot> snapshots = parseSnapshots(
                scheme.getCatalogSnapshotJson()
        );
        return toResponse(scheme, snapshots, resolveDiyVisuals(snapshots));
    }

    private DiySchemeResponse toResponse(
            DiyScheme scheme,
            Map<Long, DiyVisualView> diyVisuals
    ) {
        return toResponse(
                scheme,
                parseSnapshots(scheme.getCatalogSnapshotJson()),
                diyVisuals
        );
    }

    private DiySchemeResponse toResponse(
            DiyScheme scheme,
            List<CatalogSnapshot> snapshots,
            Map<Long, DiyVisualView> diyVisuals
    ) {
        List<Long> sequence = parseSequence(scheme.getSequenceJson());
        DiyEvaluation evaluation = pricingService.evaluate(new DiyDesignCommand(
                scheme.getWristCircumferenceMm(),
                scheme.getFitType(),
                scheme.getCordOptionId(),
                sequence
        ));
        QuoteStatus currentStatus = currentQuoteStatus(scheme, evaluation);
        return new DiySchemeResponse(
                scheme.getId(),
                scheme.getName(),
                scheme.getWristCircumferenceMm(),
                scheme.getFitType(),
                scheme.getCordOptionId(),
                sequence,
                sequence.size(),
                evaluation.skuQuantities(),
                snapshots.stream()
                        .map(snapshot -> toSnapshotView(snapshot, diyVisuals))
                        .toList(),
                currentStatus,
                evaluation.currentLengthMm(),
                evaluation.targetLengthMm(),
                evaluation.deviationMm(),
                evaluation.sizeStatus(),
                quotedValue(evaluation, evaluation.beadAmount()),
                quotedValue(evaluation, evaluation.craftFee()),
                quotedValue(evaluation, evaluation.cordFee()),
                quotedValue(evaluation, evaluation.totalAmount()),
                evaluation.quotable() ? evaluation.quoteFingerprint() : null,
                scheme.getPreviewAssetId(),
                previewUrl(scheme.getPreviewAssetId()),
                scheme.getVersion(),
                scheme.getCreatedAt(),
                scheme.getUpdatedAt()
        );
    }

    private BigDecimal quotedValue(
            DiyEvaluation evaluation,
            BigDecimal value
    ) {
        return evaluation.quotable() ? value : null;
    }

    private QuoteStatus currentQuoteStatus(
            DiyScheme scheme,
            DiyEvaluation evaluation
    ) {
        if (!evaluation.quotable()) {
            return QuoteStatus.INVALID;
        }
        return Objects.equals(
                scheme.getQuoteFingerprint(),
                evaluation.quoteFingerprint()
        ) ? QuoteStatus.VALID : QuoteStatus.STALE;
    }

    private void requireActiveAsset(Long assetId) {
        AssetFile assetFile = assetFileMapper.selectById(assetId);
        if (assetFile == null || !"ACTIVE".equals(assetFile.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "预览图不存在"
            );
        }
    }

    private String previewUrl(Long assetId) {
        if (assetId == null) {
            return null;
        }
        AssetFile assetFile = assetFileMapper.selectById(assetId);
        return assetFile != null && "ACTIVE".equals(assetFile.getStatus())
                ? assetFile.getPublicUrl()
                : null;
    }

    private List<CatalogSnapshot> snapshotCatalog(List<Long> sequence) {
        List<Long> skuIds = sequence.stream().distinct().toList();
        if (skuIds.isEmpty()) {
            return List.of();
        }
        Map<Long, BeadSku> beadsById = beadSkuMapper.selectBatchIds(skuIds)
                .stream()
                .collect(Collectors.toMap(
                        BeadSku::getId,
                        Function.identity()
                ));
        Map<Long, String> coverUrls = loadCoverUrls(beadsById.values()
                .stream()
                .map(BeadSku::getCoverAssetId)
                .toList());
        List<CatalogSnapshot> snapshots = new ArrayList<>();
        for (Long skuId : skuIds) {
            BeadSku bead = beadsById.get(skuId);
            if (bead == null) {
                snapshots.add(new CatalogSnapshot(
                        skuId,
                        "SKU #" + skuId,
                        "SKU #" + skuId,
                        null,
                        null,
                        null,
                        null
                ));
            } else {
                snapshots.add(new CatalogSnapshot(
                        bead.getId(),
                        bead.getSkuCode(),
                        bead.getName(),
                        bead.getColor(),
                        bead.getDiameterMm(),
                        coverUrls.get(bead.getCoverAssetId()),
                        bead.getStatus()
                ));
            }
        }
        return snapshots;
    }

    private Map<Long, String> loadCoverUrls(List<Long> assetIds) {
        List<Long> ids = assetIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return assetFileMapper.selectBatchIds(ids)
                .stream()
                .filter(asset -> "ACTIVE".equals(asset.getStatus()))
                .collect(Collectors.toMap(
                        AssetFile::getId,
                        AssetFile::getPublicUrl
                ));
    }

    private Map<Long, DiyVisualView> resolveDiyVisuals(
            List<CatalogSnapshot> snapshots
    ) {
        List<Long> skuIds = snapshots.stream()
                .map(CatalogSnapshot::skuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (skuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return publishedBeadVisualQuery.resolve(
                beadSkuMapper.selectBatchIds(skuIds)
        );
    }

    private DiySchemeResponse.CatalogSnapshotView toSnapshotView(
            CatalogSnapshot snapshot,
            Map<Long, DiyVisualView> diyVisuals
    ) {
        return new DiySchemeResponse.CatalogSnapshotView(
                snapshot.skuId(),
                snapshot.skuCode(),
                snapshot.name(),
                snapshot.color(),
                snapshot.diameterMm(),
                snapshot.coverUrl(),
                snapshot.status(),
                diyVisuals.get(snapshot.skuId())
        );
    }

    private String copyName(String sourceName) {
        String suffixName = sourceName + COPY_SUFFIX;
        if (suffixName.length() <= MAX_NAME_LENGTH) {
            return suffixName;
        }
        return sourceName.substring(
                0,
                MAX_NAME_LENGTH - COPY_SUFFIX.length()
        ) + COPY_SUFFIX;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("DIY 方案 JSON 序列化失败", exception);
        }
    }

    private List<Long> parseSequence(String json) {
        try {
            return List.copyOf(objectMapper.readValue(json, LONG_LIST_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("DIY 顺序数组不是有效 JSON", exception);
        }
    }

    private List<CatalogSnapshot> parseSnapshots(String json) {
        try {
            return List.copyOf(objectMapper.readValue(json, SNAPSHOT_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("DIY 商品快照不是有效 JSON", exception);
        }
    }

    /**
     * 保存时的商品展示快照。
     */
    private record CatalogSnapshot(
            Long skuId,
            String skuCode,
            String name,
            String color,
            BigDecimal diameterMm,
            String coverUrl,
            CatalogStatus status
    ) {
    }
}
