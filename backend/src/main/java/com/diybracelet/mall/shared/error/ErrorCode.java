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
