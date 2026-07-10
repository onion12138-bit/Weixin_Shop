package com.diybracelet.mall.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台发货请求。
 *
 * @param logisticsCompany 模拟物流公司名称
 * @param trackingNo 模拟物流运单号
 */
public record ShipOrderRequest(
        @NotBlank(message = "物流公司不能为空")
        @Size(max = 80, message = "物流公司不能超过 80 个字符")
        String logisticsCompany,

        @NotBlank(message = "运单号不能为空")
        @Size(max = 120, message = "运单号不能超过 120 个字符")
        String trackingNo
) {
}
