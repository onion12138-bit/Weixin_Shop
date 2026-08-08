package com.diybracelet.mall.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 小程序创建订单请求。
 *
 * @param schemeId 需要下单的 DIY 方案主键
 * @param couponId 可选的顾客优惠券主键
 * @param contactName 收货人
 * @param contactPhone 联系电话
 * @param addressDetail 收货地址
 */
public record CreateOrderRequest(
        @NotNull(message = "方案不能为空")
        @Positive(message = "方案主键必须大于零")
        Long schemeId,

        @Positive(message = "优惠券主键必须大于零")
        Long couponId,

        @NotBlank(message = "收货人不能为空")
        @Size(max = 80, message = "收货人不能超过 80 个字符")
        String contactName,

        @NotBlank(message = "联系电话不能为空")
        @Size(max = 40, message = "联系电话不能超过 40 个字符")
        String contactPhone,

        @NotBlank(message = "收货地址不能为空")
        @Size(max = 500, message = "收货地址不能超过 500 个字符")
        String addressDetail
) {
}
