package org.zmy.observabilityplatform.incident.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
public final class AnomalyPolicy {
    public static final String GLOBAL_DEFAULT_ID = "global-default";

    private final String id;
    private final String name;
    private final AnomalyPolicyScope scope;
    private final String service;
    private final String environment;
    private final String operation;
    private final boolean enabled;
    private final AnomalyDetectionSettings settings;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AnomalyPolicy(String id, String name, AnomalyPolicyScope scope, String service,
                          String environment, String operation, boolean enabled,
                          AnomalyDetectionSettings settings, long version,
                          Instant createdAt, Instant updatedAt) {
        this.id = requireText(id, "id", 36);
        this.name = requireText(name, "name", 120);
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        ScopeValues values = validateScope(scope, service, environment, operation);
        this.service = values.service;
        this.environment = values.environment;
        this.operation = values.operation;
        this.enabled = enabled;
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public static AnomalyPolicy create(String id, String name, AnomalyPolicyScope scope,
                                       String service, String environment, String operation,
                                       boolean enabled, AnomalyDetectionSettings settings, Instant now) {
        return new AnomalyPolicy(id, name, scope, service, environment, operation,
                enabled, settings, 1, now, now);
    }

    public static AnomalyPolicy globalDefault(Instant now) {
        return create(GLOBAL_DEFAULT_ID, "Global default", AnomalyPolicyScope.GLOBAL,
                null, null, null, true, AnomalyDetectionSettings.defaults(), now);
    }

    public static AnomalyPolicy restore(String id, String name, AnomalyPolicyScope scope,
                                        String service, String environment, String operation,
                                        boolean enabled, AnomalyDetectionSettings settings, long version,
                                        Instant createdAt, Instant updatedAt) {
        return new AnomalyPolicy(id, name, scope, service, environment, operation,
                enabled, settings, version, createdAt, updatedAt);
    }

    public AnomalyPolicy revise(String nextName, AnomalyPolicyScope nextScope, String nextService,
                                String nextEnvironment, String nextOperation, boolean nextEnabled,
                                AnomalyDetectionSettings nextSettings, Instant changedAt) {
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        if (GLOBAL_DEFAULT_ID.equals(id) && nextScope != AnomalyPolicyScope.GLOBAL) {
            throw new IllegalArgumentException("Global default policy scope cannot be changed");
        }
        if (changedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt must not be before updatedAt");
        }
        return new AnomalyPolicy(id, nextName, nextScope, nextService, nextEnvironment, nextOperation,
                nextEnabled, nextSettings, version + 1, createdAt, changedAt);
    }

    public AnomalyPolicyReference reference() {
        return new AnomalyPolicyReference(id, version);
    }

    public String scopeKey() {
        return scopeKeyOf(scope, service, environment, operation);
    }

    public static String scopeKeyOf(AnomalyPolicyScope scope, String service,
                                    String environment, String operation) {
        ScopeValues values = validateScope(scope, service, environment, operation);
        String canonical = String.join("\u0000", scope.name(), text(values.service),
                text(values.environment), text(values.operation));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static ScopeValues validateScope(AnomalyPolicyScope scope, String service,
                                             String environment, String operation) {
        Objects.requireNonNull(scope, "scope must not be null");
        String normalizedService = blankToNull(service);
        String normalizedEnvironment = blankToNull(environment);
        String normalizedOperation = blankToNull(operation);
        switch (scope) {
            case GLOBAL -> {
                if (normalizedService != null || normalizedEnvironment != null || normalizedOperation != null) {
                    throw new IllegalArgumentException("Global policy must not define service, environment or operation");
                }
            }
            case SERVICE -> {
                normalizedService = requireText(normalizedService, "service", 120);
                normalizedEnvironment = requireText(normalizedEnvironment, "environment", 80);
                if (normalizedOperation != null) {
                    throw new IllegalArgumentException("Service policy must not define operation");
                }
            }
            case OPERATION -> {
                normalizedService = requireText(normalizedService, "service", 120);
                normalizedEnvironment = requireText(normalizedEnvironment, "environment", 80);
                normalizedOperation = requireText(normalizedOperation, "operation", 255);
            }
        }
        return new ScopeValues(normalizedService, normalizedEnvironment, normalizedOperation);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static final class ScopeValues {
        private final String service;
        private final String environment;
        private final String operation;

        private ScopeValues(String service, String environment, String operation) {
            this.service = service;
            this.environment = environment;
            this.operation = operation;
        }
    }
}
