package org.zmy.observabilityplatform.shared.interfaces.rest;

import io.r2dbc.spi.R2dbcTransientException;
import lombok.extern.slf4j.Slf4j;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import org.zmy.observabilityplatform.shared.exception.RetryableDependencyException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    /**
     * 将资源不存在异常转换为统一的 404 响应。
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException exception) {
        return response(ApiErrorCode.RESOURCE_NOT_FOUND, exception.getMessage(), List.of());
    }

    /**
     * 将参数校验和请求解析异常转换为统一的 400 响应。
     */
    @ExceptionHandler({IllegalArgumentException.class, ServerWebInputException.class})
    public ResponseEntity<ApiError> badRequest(Exception exception) {
        return response(ApiErrorCode.INVALID_REQUEST, exception.getMessage(), List.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, WebExchangeBindException.class})
    public ResponseEntity<ApiError> validation(Exception exception) {
        List<String> details;
        if (exception instanceof MethodArgumentNotValidException validation) {
            details = validation.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .toList();
        } else {
            WebExchangeBindException validation = (WebExchangeBindException) exception;
            details = validation.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .toList();
        }
        return response(ApiErrorCode.INVALID_REQUEST, "Request validation failed", details);
    }

    /**
     * 将业务状态冲突转换为统一的 409 响应。
     */
    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ApiError> conflict(BusinessConflictException exception) {
        return response(ApiErrorCode.BUSINESS_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(RetryableDependencyException.class)
    public ResponseEntity<ApiError> retryableDependency(RetryableDependencyException exception) {
        ApiErrorCode code = switch (exception.getFailureType()) {
            case RATE_LIMITED -> ApiErrorCode.DEPENDENCY_RATE_LIMITED;
            case TIMEOUT -> ApiErrorCode.DEPENDENCY_TIMEOUT;
            case UNAVAILABLE -> ApiErrorCode.DEPENDENCY_UNAVAILABLE;
        };
        log.warn("Retryable dependency failure: {}", exception.getMessage(), exception);
        return response(code, exception.getMessage(), List.of());
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiError> dependencyResponse(WebClientResponseException exception) {
        int status = exception.getStatusCode().value();
        ApiErrorCode code;
        if (status == 429) {
            code = ApiErrorCode.DEPENDENCY_RATE_LIMITED;
        } else if (status == 408 || status == 504) {
            code = ApiErrorCode.DEPENDENCY_TIMEOUT;
        } else if (status == 500 || status == 502 || status == 503) {
            code = ApiErrorCode.DEPENDENCY_UNAVAILABLE;
        } else {
            code = ApiErrorCode.DEPENDENCY_REJECTED;
        }
        log.warn("Dependency returned HTTP status {}", status, exception);
        return response(code, dependencyMessage(code), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        ApiErrorCode retryableCode = classifyRetryable(exception);
        if (retryableCode != null) {
            log.warn("Retryable infrastructure failure", exception);
            return response(retryableCode, dependencyMessage(retryableCode), List.of());
        }
        log.error("Unhandled server error", exception);
        return response(ApiErrorCode.INTERNAL_ERROR, "Unexpected internal server error", List.of());
    }

    private ApiErrorCode classifyRetryable(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof QueryTimeoutException
                    || current instanceof TimeoutException
                    || current instanceof SocketTimeoutException) {
                return ApiErrorCode.DEPENDENCY_TIMEOUT;
            }
            if (current instanceof TransientDataAccessException
                    || current instanceof R2dbcTransientException
                    || current instanceof WebClientRequestException
                    || current instanceof org.apache.kafka.common.errors.RetriableException
                    || current instanceof ConnectException) {
                return ApiErrorCode.DEPENDENCY_UNAVAILABLE;
            }
            current = current.getCause();
        }
        return null;
    }

    private String dependencyMessage(ApiErrorCode code) {
        return switch (code) {
            case DEPENDENCY_RATE_LIMITED -> "A downstream dependency is rate limited";
            case DEPENDENCY_TIMEOUT -> "A downstream dependency timed out";
            case DEPENDENCY_UNAVAILABLE -> "A downstream dependency is temporarily unavailable";
            case DEPENDENCY_REJECTED -> "A downstream dependency rejected the request";
            default -> "Dependency request failed";
        };
    }

    private ResponseEntity<ApiError> response(ApiErrorCode code, String message, List<String> details) {
        ApiError body = new ApiError(code.name(), message, code.isRetryable(), Instant.now(), List.copyOf(details));
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }

    @Value
    public static class ApiError {
        String code;
        String message;
        boolean retryable;
        Instant timestamp;
        List<String> details;
    }
}
