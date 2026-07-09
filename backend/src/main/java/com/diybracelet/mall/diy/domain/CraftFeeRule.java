package com.diybracelet.mall.diy.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 按珠数区间维护的工艺费规则。
 *
 * <p>本阶段工艺费为固定费用，不随 SKU 单价变化。启用规则必须连续覆盖 1 到当前最大珠数，
 * 服务层负责校验区间重叠、空档和版本递增。</p>
 */
@TableName("craft_fee_rule")
public class CraftFeeRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer minBeadCount;

    private Integer maxBeadCount;

    private BigDecimal fee;

    private OptionStatus status;

    private Integer sortOrder;

    private Long ruleVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public CraftFeeRule() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getMinBeadCount() {
        return minBeadCount;
    }

    public void setMinBeadCount(Integer minBeadCount) {
        this.minBeadCount = minBeadCount;
    }

    public Integer getMaxBeadCount() {
        return maxBeadCount;
    }

    public void setMaxBeadCount(Integer maxBeadCount) {
        this.maxBeadCount = maxBeadCount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public OptionStatus getStatus() {
        return status;
    }

    public void setStatus(OptionStatus status) {
        this.status = status;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(Long ruleVersion) {
        this.ruleVersion = ruleVersion;
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
