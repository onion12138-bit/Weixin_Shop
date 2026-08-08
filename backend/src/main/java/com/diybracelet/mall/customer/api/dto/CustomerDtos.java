package com.diybracelet.mall.customer.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 顾客中心接口使用的稳定数据契约。 */
public final class CustomerDtos {
    private CustomerDtos() { }

    public record ProfileView(
            Long customerIdentityId,
            String nickname,
            Long avatarAssetId,
            String avatarUrl
    ) { }

    public record SaveProfileRequest(
            @NotBlank(message = "昵称不能为空")
            @Size(max = 40, message = "昵称不能超过 40 个字符")
            String nickname,
            @Positive(message = "头像素材主键必须大于零")
            Long avatarAssetId
    ) { }

    public record AddressView(
            Long id,
            String receiverName,
            String phone,
            String region,
            String detail,
            Boolean isDefault,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) { }

    public record SaveAddressRequest(
            @NotBlank(message = "收货人不能为空")
            @Size(max = 40, message = "收货人不能超过 40 个字符")
            String receiverName,
            @NotBlank(message = "联系电话不能为空")
            @Size(max = 30, message = "联系电话不能超过 30 个字符")
            String phone,
            @NotBlank(message = "所在地区不能为空")
            @Size(max = 120, message = "所在地区不能超过 120 个字符")
            String region,
            @NotBlank(message = "详细地址不能为空")
            @Size(max = 240, message = "详细地址不能超过 240 个字符")
            String detail,
            Boolean isDefault
    ) { }

    public record ChangeStatusRequest(
            @NotBlank(message = "状态不能为空")
            String status
    ) { }

    public record CustomerSummary(
            Long id,
            String nickname,
            String avatarUrl,
            String phone,
            String address,
            Long addressCount,
            String source,
            LocalDateTime createdAt
    ) { }

    public record AdminAddressView(
            Long id,
            Long customerIdentityId,
            String customerNickname,
            String receiverName,
            String phone,
            String region,
            String detail,
            Boolean isDefault,
            String status,
            LocalDateTime updatedAt
    ) { }

    public record CouponTemplateView(
            Long id,
            String code,
            String name,
            String description,
            BigDecimal discountAmount,
            BigDecimal minimumSpend,
            Integer validDays,
            String status,
            Long issuedCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) { }

    public record SaveCouponTemplateRequest(
            @NotBlank(message = "优惠券编码不能为空")
            @Pattern(
                    regexp = "[A-Z0-9]+(?:-[A-Z0-9]+)*",
                    message = "优惠券编码只能包含大写字母、数字和连字符"
            )
            @Size(max = 64, message = "优惠券编码不能超过 64 个字符")
            String code,
            @NotBlank(message = "优惠券名称不能为空")
            @Size(max = 80, message = "优惠券名称不能超过 80 个字符")
            String name,
            @NotBlank(message = "优惠券说明不能为空")
            @Size(max = 240, message = "优惠券说明不能超过 240 个字符")
            String description,
            @NotNull(message = "优惠金额不能为空")
            @DecimalMin(value = "0.01", message = "优惠金额必须大于 0")
            BigDecimal discountAmount,
            @NotNull(message = "使用门槛不能为空")
            @DecimalMin(value = "0.00", message = "使用门槛不能为负数")
            BigDecimal minimumSpend,
            @NotNull(message = "有效天数不能为空")
            @Min(value = 1, message = "有效天数不能少于 1 天")
            @Max(value = 3650, message = "有效天数不能超过 3650 天")
            Integer validDays,
            @NotBlank(message = "状态不能为空")
            String status
    ) { }

    public record GrantCouponRequest(
            @NotNull(message = "顾客不能为空")
            @Positive(message = "顾客主键必须大于零")
            Long customerIdentityId,
            @Size(max = 120, message = "发券备注不能超过 120 个字符")
            String note
    ) { }

    public record BatchGrantCouponRequest(
            @NotBlank(message = "发送范围不能为空")
            String recipientMode,
            @Size(max = 5000, message = "单次最多选择 5000 位顾客")
            List<@Positive(message = "顾客主键必须大于零") Long>
                    customerIdentityIds,
            @Size(max = 120, message = "发券备注不能超过 120 个字符")
            String note
    ) { }

    public record BatchGrantCouponResult(
            int requestedCount,
            int grantedCount,
            int skippedCount
    ) { }

    public record CustomerCouponView(
            Long id,
            Long templateId,
            String code,
            String name,
            String description,
            BigDecimal discountAmount,
            BigDecimal minimumSpend,
            String status,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt,
            String note
    ) { }
}
