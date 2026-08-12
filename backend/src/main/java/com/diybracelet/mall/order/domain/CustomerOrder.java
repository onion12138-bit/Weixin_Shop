package com.diybracelet.mall.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 顾客订单主表。
 *
 * <p>订单保存创建时的方案和价格快照。后续商品改名、调价或下架不会影响已创建订单，
 * 但创建订单本身必须重新执行权威报价。</p>
 */
@TableName("customer_order")
public class CustomerOrder {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long customerIdentityId;

    private Long diySchemeId;

    private OrderStatus status;

    private String contactName;

    private String contactPhone;

    private String addressDetail;

    private String orderNote;

    private String schemeSnapshotJson;

    private BigDecimal beadAmount;

    private BigDecimal craftFee;

    private BigDecimal cordFee;

    private Long couponId;

    private BigDecimal discountAmount;

    private BigDecimal shippingFee;

    private BigDecimal totalAmount;

    private LocalDateTime paidAt;

    private LocalDateTime cancelledAt;

    private LocalDateTime shippedAt;

    private LocalDateTime completedAt;

    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public CustomerOrder() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getCustomerIdentityId() {
        return customerIdentityId;
    }

    public void setCustomerIdentityId(Long customerIdentityId) {
        this.customerIdentityId = customerIdentityId;
    }

    public Long getDiySchemeId() {
        return diySchemeId;
    }

    public void setDiySchemeId(Long diySchemeId) {
        this.diySchemeId = diySchemeId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public void setAddressDetail(String addressDetail) {
        this.addressDetail = addressDetail;
    }

    public String getOrderNote() {
        return orderNote;
    }

    public void setOrderNote(String orderNote) {
        this.orderNote = orderNote;
    }

    public String getSchemeSnapshotJson() {
        return schemeSnapshotJson;
    }

    public void setSchemeSnapshotJson(String schemeSnapshotJson) {
        this.schemeSnapshotJson = schemeSnapshotJson;
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

    public Long getCouponId() {
        return couponId;
    }

    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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
