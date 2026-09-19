package org.zmy.observabilityplatform.shared.interfaces.rest;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ApiErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, false),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    BUSINESS_CONFLICT(HttpStatus.CONFLICT, false),
    DEPENDENCY_RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, true),
    DEPENDENCY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, true),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, true),
    DEPENDENCY_REJECTED(HttpStatus.BAD_GATEWAY, false),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, false);

    private final HttpStatus httpStatus;
    private final boolean retryable;

    ApiErrorCode(HttpStatus httpStatus, boolean retryable) {
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }
}
