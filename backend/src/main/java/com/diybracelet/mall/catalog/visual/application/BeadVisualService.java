package com.diybracelet.mall.catalog.visual.application;

import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.catalog.visual.api.dto.BeadVisualHistoryView;
import com.diybracelet.mall.catalog.visual.api.dto.BeadVisualVersionView;
import com.diybracelet.mall.catalog.visual.api.dto.CreateManualBeadVisualRequest;
import com.diybracelet.mall.catalog.visual.domain.BeadVisualSourceType;
import com.diybracelet.mall.catalog.visual.domain.BeadVisualStatus;
import com.diybracelet.mall.catalog.visual.domain.BeadVisualVersion;
import com.diybracelet.mall.catalog.visual.infrastructure.BeadVisualVersionMapper;
import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;

/** 管理员人工视觉版本的创建、发布、回滚与审计读取。 */
@Service
public class BeadVisualService {

    private final BeadSkuMapper beadSkuMapper;
    private final BeadVisualVersionMapper versionMapper;
    private final AssetFileMapper assetFileMapper;
    private final VisualAssetValidator visualAssetValidator;
    private final Clock clock = Clock.systemDefaultZone();

    public BeadVisualService(
            BeadSkuMapper beadSkuMapper,
            BeadVisualVersionMapper versionMapper,
            AssetFileMapper assetFileMapper,
            VisualAssetValidator visualAssetValidator
    ) {
        this.beadSkuMapper = beadSkuMapper;
        this.versionMapper = versionMapper;
        this.assetFileMapper = assetFileMapper;
        this.visualAssetValidator = visualAssetValidator;
    }

    @Transactional
    public BeadVisualVersionView createManual(
            Long skuId,
            CreateManualBeadVisualRequest request,
            Authentication authentication
    ) {
        BeadSku bead = requireLockedBead(skuId);
        BeadVisualVersion version = createManualVersion(
                bead,
                request.trayAssetId(),
                request.canvasAssetId(),
                actor(authentication)
        );
        return toView(versionMapper.selectById(version.getId()));
    }

    @Transactional(readOnly = true)
    public BeadVisualHistoryView history(Long skuId) {
        BeadSku bead = requireBead(skuId);
        List<BeadVisualVersionView> versions = versionMapper
                .selectByBeadSkuId(skuId)
                .stream()
                .map(this::toView)
                .toList();
        return new BeadVisualHistoryView(
                bead.getPublishedVisualVersionId(),
                versions
        );
    }

    @Transactional
    public BeadVisualVersionView publish(
            Long skuId,
            Long versionId,
            Authentication authentication
    ) {
        BeadSku bead = requireLockedBead(skuId);
        BeadVisualVersion candidate = requireLockedVersion(versionId);
        requireBelongsTo(candidate, bead);
        requireStatus(candidate, BeadVisualStatus.READY);
        publishLocked(bead, candidate, actor(authentication));
        return toView(candidate);
    }

    @Transactional
    public BeadVisualVersionView rollback(
            Long skuId,
            Long versionId,
            Authentication authentication
    ) {
        BeadSku bead = requireLockedBead(skuId);
        BeadVisualVersion historical = requireLockedVersion(versionId);
        requireBelongsTo(historical, bead);
        if (historical.getStatus() != BeadVisualStatus.PUBLISHED
                && historical.getStatus() != BeadVisualStatus.ARCHIVED) {
            throw conflict("只能回滚已发布或已归档的视觉版本");
        }
        BeadVisualVersion replacement = createManualVersion(
                bead,
                historical.getTrayAssetId(),
                historical.getCanvasAssetId(),
                actor(authentication)
        );
        publishLocked(bead, replacement, actor(authentication));
        return toView(versionMapper.selectById(replacement.getId()));
    }

    private BeadVisualVersion createManualVersion(
            BeadSku bead,
            Long trayAssetId,
            Long canvasAssetId,
            String actor
    ) {
        AssetFile tray = requireActiveAsset(trayAssetId);
        AssetFile canvas = requireActiveAsset(canvasAssetId);
        visualAssetValidator.validateTray(tray);
        visualAssetValidator.validateCanvas(canvas);
        int versionNo = versionMapper.selectByBeadSkuId(bead.getId())
                .stream()
                .map(BeadVisualVersion::getVersionNo)
                .filter(number -> number != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        BeadVisualVersion version = new BeadVisualVersion();
        version.setBeadSkuId(bead.getId());
        version.setVersionNo(versionNo);
        version.setSourceType(BeadVisualSourceType.MANUAL);
        version.setStatus(BeadVisualStatus.READY);
        version.setTrayAssetId(trayAssetId);
        version.setCanvasAssetId(canvasAssetId);
        version.setCreatedBy(actor);
        versionMapper.insert(version);
        return version;
    }

    private void publishLocked(
            BeadSku bead,
            BeadVisualVersion candidate,
            String actor
    ) {
        requirePublishableAssets(candidate);
        Long previousId = bead.getPublishedVisualVersionId();
        if (previousId != null) {
            BeadVisualVersion previous = versionMapper.selectByIdForUpdate(
                    previousId
            );
            if (previous == null || !bead.getId().equals(previous.getBeadSkuId())) {
                throw conflict("当前发布视觉版本无效");
            }
            previous.setStatus(BeadVisualStatus.ARCHIVED);
            versionMapper.updateById(previous);
        }
        candidate.setStatus(BeadVisualStatus.PUBLISHED);
        candidate.setPublishedBy(actor);
        candidate.setPublishedAt(LocalDateTime.now(clock));
        versionMapper.updateById(candidate);
        bead.setPublishedVisualVersionId(candidate.getId());
        beadSkuMapper.updateById(bead);
    }

    private void requirePublishableAssets(BeadVisualVersion version) {
        visualAssetValidator.validateTray(requireActiveAsset(
                version.getTrayAssetId()
        ));
        visualAssetValidator.validateCanvas(requireActiveAsset(
                version.getCanvasAssetId()
        ));
    }

    private BeadSku requireLockedBead(Long skuId) {
        BeadSku bead = beadSkuMapper.selectByIdForUpdate(skuId);
        if (bead == null) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND, "散珠不存在");
        }
        return bead;
    }

    private BeadSku requireBead(Long skuId) {
        BeadSku bead = beadSkuMapper.selectById(skuId);
        if (bead == null) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND, "散珠不存在");
        }
        return bead;
    }

    private BeadVisualVersion requireLockedVersion(Long versionId) {
        BeadVisualVersion version = versionMapper.selectByIdForUpdate(versionId);
        if (version == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "视觉版本不存在"
            );
        }
        return version;
    }

    private AssetFile requireActiveAsset(Long assetId) {
        AssetFile asset = assetFileMapper.selectById(assetId);
        if (asset == null || !"ACTIVE".equals(asset.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "视觉素材不存在或不可用"
            );
        }
        return asset;
    }

    private void requireBelongsTo(BeadVisualVersion version, BeadSku bead) {
        if (!bead.getId().equals(version.getBeadSkuId())) {
            throw conflict("视觉版本不属于该散珠");
        }
    }

    private void requireStatus(
            BeadVisualVersion version,
            BeadVisualStatus expected
    ) {
        if (version.getStatus() != expected) {
            throw conflict("视觉版本当前状态不能发布");
        }
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.COMMON_UNAUTHORIZED, "请先登录");
        }
        return authentication.getName();
    }

    private BeadVisualVersionView toView(BeadVisualVersion version) {
        return new BeadVisualVersionView(
                version.getId(),
                version.getVersionNo(),
                version.getSourceType(),
                version.getStatus(),
                version.getTrayAssetId(),
                assetUrl(version.getTrayAssetId()),
                version.getCanvasAssetId(),
                assetUrl(version.getCanvasAssetId()),
                version.getFailureCode(),
                version.getFailureMessage(),
                version.getCreatedBy(),
                version.getPublishedBy(),
                version.getPublishedAt(),
                version.getCreatedAt()
        );
    }

    private String assetUrl(Long assetId) {
        if (assetId == null) {
            return null;
        }
        AssetFile asset = assetFileMapper.selectById(assetId);
        return asset != null && "ACTIVE".equals(asset.getStatus())
                ? asset.getPublicUrl()
                : null;
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.COMMON_CONFLICT, message);
    }
}
