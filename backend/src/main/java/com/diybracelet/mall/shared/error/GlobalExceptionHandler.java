package com.diybracelet.mall.shared.error;

import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /**
     * 将 MVC 参数、请求体和类型转换失败统一映射为稳定的 400 响应。
     *
     * @param exception Spring MVC 或 Bean Validation 抛出的参数异常
     * @return 统一错误响应
     */
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
        Exception exception
    ) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity
            .status(ErrorCode.COMMON_BAD_REQUEST.status())
            .body(ApiResponse.failure(
                ErrorCode.COMMON_BAD_REQUEST.name(),
                "请求参数不合法",
                traceId
            ));
    }

    /**
     * 捕获并发请求穿过应用层预检查后触发的数据库唯一键或外键冲突。
     *
     * @param exception 数据完整性异常；响应中不暴露数据库表名和 SQL
     * @return 稳定的 409 响应
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataConflict(
        DataIntegrityViolationException exception
    ) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity
            .status(ErrorCode.COMMON_CONFLICT.status())
            .body(ApiResponse.failure(
                ErrorCode.COMMON_CONFLICT.name(),
                "数据与现有记录冲突",
                traceId
            ));
    }
}
