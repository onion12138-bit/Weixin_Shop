package com.diybracelet.mall.catalog.visual.query;

import com.diybracelet.mall.catalog.api.dto.DiyVisualView;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.visual.domain.BeadVisualStatus;
import com.diybracelet.mall.catalog.visual.domain.BeadVisualVersion;
import com.diybracelet.mall.catalog.visual.infrastructure.BeadVisualVersionMapper;
import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 批量解析 SKU 当前已发布的 DIY 视觉稿，避免目录读取出现 N+1 查询。 */
@Component
public class PublishedBeadVisualQuery {

    private final BeadVisualVersionMapper versionMapper;
    private final AssetFileMapper assetFileMapper;

    public PublishedBeadVisualQuery(
            BeadVisualVersionMapper versionMapper,
            AssetFileMapper assetFileMapper
    ) {
        this.versionMapper = versionMapper;
        this.assetFileMapper = assetFileMapper;
    }

    public Map<Long, DiyVisualView> resolve(List<BeadSku> beads) {
        if (beads == null || beads.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> versionIds = beads.stream()
                .map(BeadSku::getPublishedVisualVersionId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (versionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, BeadVisualVersion> versions = versionMapper
                .selectBatchIds(versionIds)
                .stream()
                .collect(Collectors.toMap(
                        BeadVisualVersion::getId,
                        Function.identity()
                ));
        List<BeadVisualVersion> publishable = new ArrayList<>();
        for (BeadSku bead : beads) {
            BeadVisualVersion version = versions.get(
                    bead.getPublishedVisualVersionId()
            );
            if (version != null
                    && version.getStatus() == BeadVisualStatus.PUBLISHED
                    && bead.getId().equals(version.getBeadSkuId())) {
                publishable.add(version);
            }
        }
        if (publishable.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> assetIds = publishable.stream()
                .flatMap(version -> java.util.stream.Stream.of(
                        version.getTrayAssetId(),
                        version.getCanvasAssetId()
                ))
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (assetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, AssetFile> assets = assetFileMapper.selectBatchIds(assetIds)
                .stream()
                .filter(asset -> "ACTIVE".equals(asset.getStatus()))
                .collect(Collectors.toMap(
                        AssetFile::getId,
                        Function.identity()
                ));
        Map<Long, DiyVisualView> resolved = new HashMap<>();
        for (BeadSku bead : beads) {
            BeadVisualVersion version = versions.get(
                    bead.getPublishedVisualVersionId()
            );
            if (version == null
                    || version.getStatus() != BeadVisualStatus.PUBLISHED
                    || !bead.getId().equals(version.getBeadSkuId())) {
                continue;
            }
            AssetFile tray = assets.get(version.getTrayAssetId());
            AssetFile canvas = assets.get(version.getCanvasAssetId());
            if (tray != null && canvas != null) {
                resolved.put(bead.getId(), new DiyVisualView(
                        tray.getPublicUrl(),
                        canvas.getPublicUrl(),
                        version.getVersionNo()
                ));
            }
        }
        return resolved;
    }
}
