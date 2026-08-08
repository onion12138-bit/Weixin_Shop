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

    /** 当前顾客保存的有效 DIY 方案数量已达到上限。 */
    DIY_SCHEME_LIMIT_REACHED(HttpStatus.CONFLICT),

    /** 当前 DIY 设计不可报价，不能进入需要确定价格的后续流程。 */
    DIY_DESIGN_NOT_QUOTABLE(HttpStatus.UNPROCESSABLE_ENTITY),

    /** 下单所需 SKU 库存不足。 */
    INVENTORY_INSUFFICIENT(HttpStatus.CONFLICT),

    /** AI Provider API Key 尚未配置。 */
    AI_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE),

    /** AI 推荐业务提示词尚未配置。 */
    AI_PROMPT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE),

    /** 服务端缺少可用的 AI 密钥加密配置。 */
    AI_SERVER_MISCONFIGURED(HttpStatus.SERVICE_UNAVAILABLE),

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

    /** AI Provider 请求超过当前配置的等待时间。 */
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),

    /** AI Provider 返回了非鉴权、非限流类上游故障。 */
    AI_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY),

    /** AI 推荐响应未通过后端契约或业务校验。 */
    AI_INVALID_RECOMMENDATION(HttpStatus.UNPROCESSABLE_ENTITY),

    /** DeepSeek 返回内容不是约定的 JSON 结构。 */
    AI_RESPONSE_FORMAT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),

    /** DeepSeek 返回的推荐套数与后台配置不一致。 */
    AI_RESULT_COUNT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),

    /** DeepSeek 返回的单套珠子种类数不在 3～5 范围内。 */
    AI_RESULT_ITEM_COUNT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),

    /** DeepSeek 在同一套方案中重复选择了同一个 SKU。 */
    AI_RESULT_DUPLICATE_SKU(HttpStatus.UNPROCESSABLE_ENTITY),

    /** DeepSeek 选择了不存在、下架或无库存的 SKU。 */
    AI_RESULT_SKU_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),

    /** DeepSeek 文案包含不允许的确定效果或健康承诺。 */
    AI_RESPONSE_CONTENT_UNSAFE(HttpStatus.UNPROCESSABLE_ENTITY),

    /** 必填生日为空。 */
    BIRTH_DATE_REQUIRED(HttpStatus.BAD_REQUEST),

    /** 生日格式不正确或晚于当前日期。 */
    BIRTH_DATE_INVALID(HttpStatus.BAD_REQUEST),

    /** 当前没有可供 AI 推荐的在售库存珠子。 */
    CATALOG_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY),

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
