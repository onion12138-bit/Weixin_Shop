package com.diybracelet.mall.catalog.visual.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 散珠固定视图视觉稿的一个版本。 */
@TableName("bead_visual_version")
public class BeadVisualVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long beadSkuId;
    private Integer versionNo;
    private BeadVisualSourceType sourceType;
    private String shapePreset;
    private String materialPreset;
    private BeadVisualStatus status;
    private Long trayAssetId;
    private Long canvasAssetId;
    private String failureCode;
    private String failureMessage;
    private String createdBy;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BeadVisualVersion() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBeadSkuId() {
        return beadSkuId;
    }

    public void setBeadSkuId(Long beadSkuId) {
        this.beadSkuId = beadSkuId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public BeadVisualSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(BeadVisualSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getShapePreset() {
        return shapePreset;
    }

    public void setShapePreset(String shapePreset) {
        this.shapePreset = shapePreset;
    }

    public String getMaterialPreset() {
        return materialPreset;
    }

    public void setMaterialPreset(String materialPreset) {
        this.materialPreset = materialPreset;
    }

    public BeadVisualStatus getStatus() {
        return status;
    }

    public void setStatus(BeadVisualStatus status) {
        this.status = status;
    }

    public Long getTrayAssetId() {
        return trayAssetId;
    }

    public void setTrayAssetId(Long trayAssetId) {
        this.trayAssetId = trayAssetId;
    }

    public Long getCanvasAssetId() {
        return canvasAssetId;
    }

    public void setCanvasAssetId(Long canvasAssetId) {
        this.canvasAssetId = canvasAssetId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(String publishedBy) {
        this.publishedBy = publishedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
