package com.diybracelet.mall.shared.error;

import java.util.Objects;

/**
 * 表示调用方可以理解并通过修改请求或业务状态来修正的失败。
 *
 * <p>该异常只承载业务语义，不负责生成 HTTP 响应；统一响应转换由
 * {@link GlobalExceptionHandler} 完成。</p>
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 创建业务异常。
     *
     * @param errorCode 稳定错误码，不能为 {@code null}
     * @param message 面向调用方的错误说明
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(
            errorCode,
            "errorCode must not be null"
        );
    }

    /**
     * 返回该异常的稳定错误码。
     *
     * @return 错误码
     */
    public ErrorCode errorCode() {
        return errorCode;
    }
}
