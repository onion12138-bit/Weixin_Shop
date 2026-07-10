package com.diybracelet.mall.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 库存流水。
 *
 * <p>每一次库存余额变化都记录变化前后数量，用于后续排查超卖、重复支付或手工调整。</p>
 */
@TableName("inventory_ledger")
public class InventoryLedger {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long beadSkuId;

    private Long orderId;

    private String changeType;

    private Integer quantity;

    private Integer beforeAvailableQty;

    private Integer afterAvailableQty;

    private Integer beforeReservedQty;

    private Integer afterReservedQty;

    private Integer beforeSoldQty;

    private Integer afterSoldQty;

    private String note;

    private LocalDateTime createdAt;

    public InventoryLedger() {
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

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getBeforeAvailableQty() {
        return beforeAvailableQty;
    }

    public void setBeforeAvailableQty(Integer beforeAvailableQty) {
        this.beforeAvailableQty = beforeAvailableQty;
    }

    public Integer getAfterAvailableQty() {
        return afterAvailableQty;
    }

    public void setAfterAvailableQty(Integer afterAvailableQty) {
        this.afterAvailableQty = afterAvailableQty;
    }

    public Integer getBeforeReservedQty() {
        return beforeReservedQty;
    }

    public void setBeforeReservedQty(Integer beforeReservedQty) {
        this.beforeReservedQty = beforeReservedQty;
    }

    public Integer getAfterReservedQty() {
        return afterReservedQty;
    }

    public void setAfterReservedQty(Integer afterReservedQty) {
        this.afterReservedQty = afterReservedQty;
    }

    public Integer getBeforeSoldQty() {
        return beforeSoldQty;
    }

    public void setBeforeSoldQty(Integer beforeSoldQty) {
        this.beforeSoldQty = beforeSoldQty;
    }

    public Integer getAfterSoldQty() {
        return afterSoldQty;
    }

    public void setAfterSoldQty(Integer afterSoldQty) {
        this.afterSoldQty = afterSoldQty;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
