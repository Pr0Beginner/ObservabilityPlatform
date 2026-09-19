package org.zmy.observabilityplatform.shared.exception;

import lombok.Getter;

@Getter
public class RetryableDependencyException extends RuntimeException {
    private final FailureType failureType;

    public RetryableDependencyException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public static RetryableDependencyException unavailable(String dependency, Throwable cause) {
        return new RetryableDependencyException(FailureType.UNAVAILABLE,
                dependency + " is temporarily unavailable", cause);
    }

    public static RetryableDependencyException timeout(String dependency, Throwable cause) {
        return new RetryableDependencyException(FailureType.TIMEOUT,
                dependency + " request timed out", cause);
    }

    public enum FailureType {
        RATE_LIMITED,
        TIMEOUT,
        UNAVAILABLE
    }
}
