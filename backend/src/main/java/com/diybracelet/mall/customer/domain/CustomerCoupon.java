package com.diybracelet.mall.customer.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.math.BigDecimal;


/** 顾客领取的优惠券及其使用状态。 */
@TableName("customer_coupon")
public class CustomerCoupon {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long couponTemplateId;
    private Long customerIdentityId;
    // 领取时冻结权益；模板编辑只影响后续发放。
    private String couponCode;
    private String couponName;
    private String couponDescription;
    private BigDecimal discountAmount;
    private BigDecimal minimumSpend;
    private String snapshotSource;
    private String grantBatchId;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getCouponTemplateId() { return couponTemplateId; }
    public void setCouponTemplateId(Long value) { couponTemplateId = value; }
    public Long getCustomerIdentityId() { return customerIdentityId; }
    public void setCustomerIdentityId(Long value) { customerIdentityId = value; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String value) { couponCode = value; }
    public String getCouponName() { return couponName; }
    public void setCouponName(String value) { couponName = value; }
    public String getCouponDescription() { return couponDescription; }
    public void setCouponDescription(String value) { couponDescription = value; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal value) { discountAmount = value; }
    public BigDecimal getMinimumSpend() { return minimumSpend; }
    public void setMinimumSpend(BigDecimal value) { minimumSpend = value; }
    public String getSnapshotSource() { return snapshotSource; }
    public void setSnapshotSource(String value) { snapshotSource = value; }
    public String getGrantBatchId() { return grantBatchId; }
    public void setGrantBatchId(String value) { grantBatchId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime value) { issuedAt = value; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { expiresAt = value; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime value) { usedAt = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
