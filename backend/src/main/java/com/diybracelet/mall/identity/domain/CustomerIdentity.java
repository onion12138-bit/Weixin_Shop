package com.diybracelet.mall.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 系统内部稳定顾客身份。
 *
 * <p>匿名访客、微信 OpenID、手机号等外部身份都会先映射到本表，再由业务模块通过
 * {@code id} 关联数据。当前只启用 {@code GUEST} 来源，不保存可直接作为凭据使用的
 * 原始安装 UUID，只保存加 pepper 后的 SHA-256 摘要。</p>
 */
@TableName("customer_identity")
public class CustomerIdentity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String source;

    private String externalKeyHash;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * MyBatis 通过无参构造器创建实体。
     */
    public CustomerIdentity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getExternalKeyHash() {
        return externalKeyHash;
    }

    public void setExternalKeyHash(String externalKeyHash) {
        this.externalKeyHash = externalKeyHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
