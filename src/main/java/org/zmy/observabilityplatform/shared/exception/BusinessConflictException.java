package org.zmy.observabilityplatform.shared.exception;

public class BusinessConflictException extends RuntimeException {
    public BusinessConflictException(String message) {
        super(message);
    }
}
