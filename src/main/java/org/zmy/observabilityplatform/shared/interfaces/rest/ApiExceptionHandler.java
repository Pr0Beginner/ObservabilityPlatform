package org.zmy.observabilityplatform.shared.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    /**
     * 将资源不存在异常转换为统一的 404 响应。
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(NotFoundException exception) {
        return error("NOT_FOUND", exception.getMessage());
    }

    /**
     * 将参数校验和请求解析异常转换为统一的 400 响应。
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            ServerWebInputException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(Exception exception) {
        return error("BAD_REQUEST", exception.getMessage());
    }

    /**
     * 将业务状态冲突转换为统一的 409 响应。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError conflict(IllegalStateException exception) {
        return error("CONFLICT", exception.getMessage());
    }

    private ApiError error(String code, String message) {
        return new ApiError(code, message, Instant.now(), List.of());
    }

    public record ApiError(String code, String message, Instant timestamp, List<String> details) { }
}
