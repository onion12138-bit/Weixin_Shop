package com.diybracelet.mall.diy.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DIY 固定视图渲染配置单例。 */
@TableName("diy_visual_config")
public class DiyVisualConfig {

    @TableId("id")
    private Long id;
    private String canvasBackgroundColor;
    private BigDecimal braceletShadowOpacity;
    private BigDecimal beadShadowOpacity;
    private BigDecimal beadOverlapRatio;
    private Long configVersion;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DiyVisualConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCanvasBackgroundColor() {
        return canvasBackgroundColor;
    }

    public void setCanvasBackgroundColor(String canvasBackgroundColor) {
        this.canvasBackgroundColor = canvasBackgroundColor;
    }

    public BigDecimal getBraceletShadowOpacity() {
        return braceletShadowOpacity;
    }

    public void setBraceletShadowOpacity(BigDecimal braceletShadowOpacity) {
        this.braceletShadowOpacity = braceletShadowOpacity;
    }

    public BigDecimal getBeadShadowOpacity() {
        return beadShadowOpacity;
    }

    public void setBeadShadowOpacity(BigDecimal beadShadowOpacity) {
        this.beadShadowOpacity = beadShadowOpacity;
    }

    public BigDecimal getBeadOverlapRatio() {
        return beadOverlapRatio;
    }

    public void setBeadOverlapRatio(BigDecimal beadOverlapRatio) {
        this.beadOverlapRatio = beadOverlapRatio;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
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
