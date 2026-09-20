package org.zmy.observabilityplatform.audit.domain.model;

import lombok.Getter;

import java.util.List;

@Getter
public final class AuditActor {
    private final String name;
    private final List<String> roles;

    public AuditActor(String name, List<String> roles) {
        this.name = requireText(name, "name");
        this.roles = roles == null ? List.of() : roles.stream().sorted().toList();
    }

    public static AuditActor anonymous() {
        return new AuditActor("anonymous", List.of());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
