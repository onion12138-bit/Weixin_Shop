package com.diybracelet.mall.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 单个散珠 SKU 的库存余额。
 *
 * <p>库存拆分为可用、预占、已售三段。下单只把可用转预占，模拟支付成功后再把预占
 * 转已售，避免用户未支付时提前消耗真实销售量。</p>
 */
@TableName("inventory_balance")
public class InventoryBalance {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long beadSkuId;

    private Integer availableQty;

    private Integer reservedQty;

    private Integer soldQty;

    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public InventoryBalance() {
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

    public Integer getAvailableQty() {
        return availableQty;
    }

    public void setAvailableQty(Integer availableQty) {
        this.availableQty = availableQty;
    }

    public Integer getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(Integer reservedQty) {
        this.reservedQty = reservedQty;
    }

    public Integer getSoldQty() {
        return soldQty;
    }

    public void setSoldQty(Integer soldQty) {
        this.soldQty = soldQty;
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
