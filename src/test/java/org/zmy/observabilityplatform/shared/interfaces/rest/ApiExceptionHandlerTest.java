package org.zmy.observabilityplatform.shared.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import org.zmy.observabilityplatform.shared.exception.RetryableDependencyException;

import java.util.EnumSet;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void classifiesEveryErrorCodeAsRetryableOrNonRetryable() {
        EnumSet<ApiErrorCode> retryable = EnumSet.of(
                ApiErrorCode.DEPENDENCY_RATE_LIMITED,
                ApiErrorCode.DEPENDENCY_TIMEOUT,
                ApiErrorCode.DEPENDENCY_UNAVAILABLE);

        for (ApiErrorCode code : ApiErrorCode.values()) {
            assertThat(code.isRetryable()).isEqualTo(retryable.contains(code));
        }
    }

    @Test
    void returnsNonRetryableBusinessErrors() {
        ResponseEntity<ApiExceptionHandler.ApiError> notFound =
                handler.notFound(new NotFoundException("Incident not found"));
        ResponseEntity<ApiExceptionHandler.ApiError> conflict =
                handler.conflict(new BusinessConflictException("Diagnosis is already active"));

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).isNotNull();
        assertThat(notFound.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(notFound.getBody().isRetryable()).isFalse();
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).isNotNull();
        assertThat(conflict.getBody().isRetryable()).isFalse();
    }

    @Test
    void returnsRetryableDependencyErrors() {
        ResponseEntity<ApiExceptionHandler.ApiError> explicitTimeout = handler.retryableDependency(
                RetryableDependencyException.timeout("MySQL", new TimeoutException("timed out")));
        ResponseEntity<ApiExceptionHandler.ApiError> nestedTimeout =
                handler.unexpected(new RuntimeException(new TimeoutException("timed out")));

        assertThat(explicitTimeout.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(explicitTimeout.getBody()).isNotNull();
        assertThat(explicitTimeout.getBody().getCode()).isEqualTo("DEPENDENCY_TIMEOUT");
        assertThat(explicitTimeout.getBody().isRetryable()).isTrue();
        assertThat(nestedTimeout.getBody()).isNotNull();
        assertThat(nestedTimeout.getBody().isRetryable()).isTrue();
    }

    @Test
    void doesNotMisclassifyUnknownIllegalStateAsBusinessConflict() {
        ResponseEntity<ApiExceptionHandler.ApiError> response =
                handler.unexpected(new IllegalStateException("Unexpected Elasticsearch response"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().isRetryable()).isFalse();
    }
}
