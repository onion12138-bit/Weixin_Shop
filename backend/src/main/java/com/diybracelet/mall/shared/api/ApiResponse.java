package com.diybracelet.mall.shared.api;

import java.time.Instant;

/**
 * 后端面向微信小程序、管理后台及后续开放接口的统一响应结构。
 *
 * <p>客户端只应依赖稳定的 {@code code} 和字段结构，不应依赖服务端异常类名。
 * {@code traceId} 用于关联客户端报错与服务端日志；当前由调用方传入，后续接入
 * 分布式追踪后可以直接复用请求上下文中的链路标识。</p>
 *
 * @param code 稳定业务码；成功固定为 {@code OK}
 * @param message 面向当前调用方的简短说明
 * @param data 成功响应的数据；失败时通常为 {@code null}
 * @param traceId 服务端链路标识
 * @param timestamp 服务端生成响应的时间
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(
    String code,
    String message,
    T data,
    String traceId,
    Instant timestamp
) {

    /**
     * 创建成功响应。
     *
     * @param data 业务数据
     * @param traceId 当前请求的追踪标识
     * @param <T> 业务数据类型
     * @return 统一成功响应
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "成功", data, traceId, Instant.now());
    }

    /**
     * 创建不携带业务数据的失败响应。
     *
     * @param code 稳定业务错误码
     * @param message 可读错误说明
     * @param traceId 当前请求的追踪标识
     * @return 统一失败响应
     */
    public static ApiResponse<Void> failure(
        String code,
        String message,
        String traceId
    ) {
        return new ApiResponse<>(code, message, null, traceId, Instant.now());
    }
}
