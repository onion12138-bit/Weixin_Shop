package com.diybracelet.mall.customer.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("customer_address")
public class CustomerAddress {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long customerIdentityId;
    private String receiverName;
    private String phone;
    private String region;
    private String detail;
    private Boolean isDefault;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getCustomerIdentityId() { return customerIdentityId; }
    public void setCustomerIdentityId(Long value) { customerIdentityId = value; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String value) { receiverName = value; }
    public String getPhone() { return phone; }
    public void setPhone(String value) { phone = value; }
    public String getRegion() { return region; }
    public void setRegion(String value) { region = value; }
    public String getDetail() { return detail; }
    public void setDetail(String value) { detail = value; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean value) { isDefault = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
