package com.diybracelet.mall.catalog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 成品套餐中的散珠明细。
 *
 * <p>{@code quantity} 表示该 SKU 在一条手串中的颗数，必须大于零；
 * {@code sortOrder} 同时决定搭配详情的展示顺序，并为后续 DIY 预览保留稳定位置。</p>
 */
@TableName("package_item")
public class PackageItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long packageId;

    private Long beadSkuId;

    private Integer quantity;

    private Integer sortOrder;

    /**
     * MyBatis 通过无参构造器创建实体。
     */
    public PackageItem() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPackageId() {
        return packageId;
    }

    public void setPackageId(Long packageId) {
        this.packageId = packageId;
    }

    public Long getBeadSkuId() {
        return beadSkuId;
    }

    public void setBeadSkuId(Long beadSkuId) {
        this.beadSkuId = beadSkuId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
