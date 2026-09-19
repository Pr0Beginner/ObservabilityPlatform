package org.zmy.observabilityplatform.incident.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Getter
@ToString
@EqualsAndHashCode
public final class MetricKey {
    private static final String ALL = "*";

    private final MetricType type;
    private final String service;
    private final String environment;
    private final String operation;
    private final String dimension;

    private MetricKey(MetricType type, String service, String environment, String operation, String dimension) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.service = requireText(service, "service", 120);
        this.environment = requireText(environment, "environment", 80);
        this.operation = normalize(operation, "operation", 255);
        this.dimension = normalize(dimension, "dimension", 255);
    }

    public static MetricKey requestTotal(String service, String environment, String operation) {
        return new MetricKey(MetricType.REQUEST_TOTAL, service, environment, operation, null);
    }

    public static MetricKey requestFailure(String service, String environment, String operation) {
        return new MetricKey(MetricType.REQUEST_FAILURE, service, environment, operation, null);
    }

    public static MetricKey errorCode(String service, String environment, String operation, String errorCode) {
        return new MetricKey(MetricType.ERROR_CODE, service, environment, operation, requireText(errorCode, "errorCode"));
    }

    public static MetricKey errorFingerprint(String service, String environment, String fingerprint) {
        return new MetricKey(MetricType.ERROR_FINGERPRINT, service, environment, null,
                requireText(fingerprint, "fingerprint"));
    }

    public String value() {
        String canonical = String.join("\u0000", type.name(), service, environment, operation, dimension);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String normalize(String value, String field, int maxLength) {
        String normalized = value == null || value.isBlank() ? ALL : value;
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        return requireText(value, field, Integer.MAX_VALUE);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
