package com.diybracelet.mall.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 后台修改尚未发货订单的收货信息。 */
public record UpdateOrderAddressRequest(
        @NotBlank @Size(max = 80) String contactName,
        @NotBlank @Size(max = 40) String contactPhone,
        @NotBlank @Size(max = 500) String addressDetail,
        @Size(max = 500) String orderNote
) { }
