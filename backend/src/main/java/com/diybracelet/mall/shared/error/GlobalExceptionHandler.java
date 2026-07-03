package com.diybracelet.mall.shared.error;

import com.diybracelet.mall.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将服务端业务异常转换为稳定的 HTTP 与 JSON 响应契约。
 *
 * <p>当前使用随机 UUID 作为 {@code traceId}。接入统一日志和链路追踪后，
 * 应优先复用请求上下文中的追踪标识，确保客户端提供的标识可以直接定位日志。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 将可预期业务异常映射为其错误码对应的 HTTP 状态。
     *
     * @param exception 业务层抛出的可预期异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
        BusinessException exception
    ) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity
            .status(exception.errorCode().status())
            .body(ApiResponse.failure(
                exception.errorCode().name(),
                exception.getMessage(),
                traceId
            ));
    }
}
