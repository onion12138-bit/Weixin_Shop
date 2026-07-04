package com.diybracelet.mall.catalog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可用于 DIY 设计的散珠 SKU。
 *
 * <p>尺寸和金额使用 {@link BigDecimal}，避免浮点数精度误差。金额单位为人民币元；
 * 数据库约束保证价格非负、珠径为正。{@code tagsJson} 暂以 JSON 字符串承载标签，
 * 后续可在不改变表结构的情况下增加类型处理器。</p>
 */
@TableName("bead_sku")
public class BeadSku {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private String skuCode;

    private String name;

    private String material;

    private String color;

    private BigDecimal diameterMm;

    private BigDecimal salePrice;

    private BigDecimal costPrice;

    private Integer stockAlertThreshold;

    private Long coverAssetId;

    private String tagsJson;

    private CatalogStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * MyBatis 通过无参构造器创建实体。
     */
    public BeadSku() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public BigDecimal getDiameterMm() {
        return diameterMm;
    }

    public void setDiameterMm(BigDecimal diameterMm) {
        this.diameterMm = diameterMm;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public Integer getStockAlertThreshold() {
        return stockAlertThreshold;
    }

    public void setStockAlertThreshold(Integer stockAlertThreshold) {
        this.stockAlertThreshold = stockAlertThreshold;
    }

    public Long getCoverAssetId() {
        return coverAssetId;
    }

    public void setCoverAssetId(Long coverAssetId) {
        this.coverAssetId = coverAssetId;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson) {
        this.tagsJson = tagsJson;
    }

    public CatalogStatus getStatus() {
        return status;
    }

    public void setStatus(CatalogStatus status) {
        this.status = status;
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
