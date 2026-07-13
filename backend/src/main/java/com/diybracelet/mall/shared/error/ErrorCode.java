package com.diybracelet.mall.shared.error;

import org.springframework.http.HttpStatus;

/**
 * 跨业务模块可复用的通用错误码。
 *
 * <p>错误码同时绑定标准 HTTP 状态，保证传输层语义与响应体业务码一致。
 * 商品、订单、支付等领域后续可以在各自模块中扩展更细的错误码体系。</p>
 */
public enum ErrorCode {

    /** 请求参数或请求格式不合法。 */
    COMMON_BAD_REQUEST(HttpStatus.BAD_REQUEST),

    /** 当前请求尚未完成身份认证。 */
    COMMON_UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    /** 当前身份无权执行请求操作。 */
    COMMON_FORBIDDEN(HttpStatus.FORBIDDEN),

    /** 请求的业务资源不存在。 */
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 请求与资源当前状态发生冲突。 */
    COMMON_CONFLICT(HttpStatus.CONFLICT),

    /** DIY 方案更新版本与服务端当前版本不一致。 */
    DIY_SCHEME_VERSION_CONFLICT(HttpStatus.CONFLICT),

    /** 当前 DIY 设计不可报价，不能进入需要确定价格的后续流程。 */
    DIY_DESIGN_NOT_QUOTABLE(HttpStatus.UNPROCESSABLE_ENTITY),

    /** 下单所需 SKU 库存不足。 */
    INVENTORY_INSUFFICIENT(HttpStatus.CONFLICT),

    /** AI Provider API Key 尚未配置。 */
    AI_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE),

    /** AI 功能已被管理员停用。 */
    AI_DISABLED(HttpStatus.SERVICE_UNAVAILABLE),

    /** AI Provider 拒绝了当前鉴权信息。 */
    AI_UNAUTHORIZED(HttpStatus.BAD_GATEWAY),

    /** 配置的 AI 模型在 Provider 侧不可用。 */
    AI_MODEL_UNAVAILABLE(HttpStatus.BAD_GATEWAY),

    /** AI Provider 对当前请求限流。 */
    AI_RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE),

    /** AI Provider 超时或发生临时故障。 */
    AI_TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    /** AI 推荐响应未通过后端契约或业务校验。 */
    AI_INVALID_RECOMMENDATION(HttpStatus.UNPROCESSABLE_ENTITY),

    /** 服务端发生未预期错误。 */
    COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    /**
     * 返回该业务错误对应的 HTTP 状态。
     *
     * @return 标准 HTTP 状态
     */
    public HttpStatus status() {
        return status;
    }
}
