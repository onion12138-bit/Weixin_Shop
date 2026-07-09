package com.diybracelet.mall.diy.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DIY 尺寸规则单例配置。
 *
 * <p>所有尺寸单位均为毫米。配置版本用于客户端识别规则是否发生变化，并在后续报价指纹
 * 中参与计算。</p>
 */
@TableName("diy_rule_config")
public class DiyRuleConfig {

    @TableId("id")
    private Long id;

    private BigDecimal minWristMm;

    private BigDecimal maxWristMm;

    private BigDecimal snugAllowanceMm;

    private BigDecimal standardAllowanceMm;

    private BigDecimal looseAllowanceMm;

    private BigDecimal toleranceMm;

    private Integer maxBeadCount;

    private Long configVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public DiyRuleConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getMinWristMm() {
        return minWristMm;
    }

    public void setMinWristMm(BigDecimal minWristMm) {
        this.minWristMm = minWristMm;
    }

    public BigDecimal getMaxWristMm() {
        return maxWristMm;
    }

    public void setMaxWristMm(BigDecimal maxWristMm) {
        this.maxWristMm = maxWristMm;
    }

    public BigDecimal getSnugAllowanceMm() {
        return snugAllowanceMm;
    }

    public void setSnugAllowanceMm(BigDecimal snugAllowanceMm) {
        this.snugAllowanceMm = snugAllowanceMm;
    }

    public BigDecimal getStandardAllowanceMm() {
        return standardAllowanceMm;
    }

    public void setStandardAllowanceMm(BigDecimal standardAllowanceMm) {
        this.standardAllowanceMm = standardAllowanceMm;
    }

    public BigDecimal getLooseAllowanceMm() {
        return looseAllowanceMm;
    }

    public void setLooseAllowanceMm(BigDecimal looseAllowanceMm) {
        this.looseAllowanceMm = looseAllowanceMm;
    }

    public BigDecimal getToleranceMm() {
        return toleranceMm;
    }

    public void setToleranceMm(BigDecimal toleranceMm) {
        this.toleranceMm = toleranceMm;
    }

    public Integer getMaxBeadCount() {
        return maxBeadCount;
    }

    public void setMaxBeadCount(Integer maxBeadCount) {
        this.maxBeadCount = maxBeadCount;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
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
