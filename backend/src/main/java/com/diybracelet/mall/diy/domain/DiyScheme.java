package com.diybracelet.mall.diy.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户保存的 DIY 手串方案。
 *
 * <p>方案只保存“SKU 顺序数组”的 JSON，不为每一颗珠子建立数据库记录。同一种 SKU
 * 的数量由服务端从顺序数组实时统计；小程序展示时可临时生成 1、2、3 这样的序号。</p>
 */
@TableName("diy_scheme")
public class DiyScheme {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long customerIdentityId;

    private String name;

    private BigDecimal wristCircumferenceMm;

    private FitType fitType;

    private Long cordOptionId;

    private String sequenceJson;

    private Integer beadCount;

    private String catalogSnapshotJson;

    private BigDecimal beadAmount;

    private BigDecimal craftFee;

    private BigDecimal cordFee;

    private BigDecimal totalAmount;

    private String quoteFingerprint;

    private QuoteStatus quoteStatus;

    private Long previewAssetId;

    private SchemeStatus status;

    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public DiyScheme() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerIdentityId() {
        return customerIdentityId;
    }

    public void setCustomerIdentityId(Long customerIdentityId) {
        this.customerIdentityId = customerIdentityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getWristCircumferenceMm() {
        return wristCircumferenceMm;
    }

    public void setWristCircumferenceMm(BigDecimal wristCircumferenceMm) {
        this.wristCircumferenceMm = wristCircumferenceMm;
    }

    public FitType getFitType() {
        return fitType;
    }

    public void setFitType(FitType fitType) {
        this.fitType = fitType;
    }

    public Long getCordOptionId() {
        return cordOptionId;
    }

    public void setCordOptionId(Long cordOptionId) {
        this.cordOptionId = cordOptionId;
    }

    public String getSequenceJson() {
        return sequenceJson;
    }

    public void setSequenceJson(String sequenceJson) {
        this.sequenceJson = sequenceJson;
    }

    public Integer getBeadCount() {
        return beadCount;
    }

    public void setBeadCount(Integer beadCount) {
        this.beadCount = beadCount;
    }

    public String getCatalogSnapshotJson() {
        return catalogSnapshotJson;
    }

    public void setCatalogSnapshotJson(String catalogSnapshotJson) {
        this.catalogSnapshotJson = catalogSnapshotJson;
    }

    public BigDecimal getBeadAmount() {
        return beadAmount;
    }

    public void setBeadAmount(BigDecimal beadAmount) {
        this.beadAmount = beadAmount;
    }

    public BigDecimal getCraftFee() {
        return craftFee;
    }

    public void setCraftFee(BigDecimal craftFee) {
        this.craftFee = craftFee;
    }

    public BigDecimal getCordFee() {
        return cordFee;
    }

    public void setCordFee(BigDecimal cordFee) {
        this.cordFee = cordFee;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getQuoteFingerprint() {
        return quoteFingerprint;
    }

    public void setQuoteFingerprint(String quoteFingerprint) {
        this.quoteFingerprint = quoteFingerprint;
    }

    public QuoteStatus getQuoteStatus() {
        return quoteStatus;
    }

    public void setQuoteStatus(QuoteStatus quoteStatus) {
        this.quoteStatus = quoteStatus;
    }

    public Long getPreviewAssetId() {
        return previewAssetId;
    }

    public void setPreviewAssetId(Long previewAssetId) {
        this.previewAssetId = previewAssetId;
    }

    public SchemeStatus getStatus() {
        return status;
    }

    public void setStatus(SchemeStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
