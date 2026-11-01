package com.diybracelet.mall.customer.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;

import java.time.LocalDateTime;


/** 顾客头像、昵称等账户资料。 */
@TableName("customer_profile")
public class CustomerProfile {
    @TableId("customer_identity_id")
    private Long customerIdentityId;
    private String nickname;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long avatarAssetId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getCustomerIdentityId() { return customerIdentityId; }
    public void setCustomerIdentityId(Long value) { customerIdentityId = value; }
    public String getNickname() { return nickname; }
    public void setNickname(String value) { nickname = value; }
    public Long getAvatarAssetId() { return avatarAssetId; }
    public void setAvatarAssetId(Long value) { avatarAssetId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
