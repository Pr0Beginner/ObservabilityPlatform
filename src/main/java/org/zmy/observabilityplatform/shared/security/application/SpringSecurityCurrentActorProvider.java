package org.zmy.observabilityplatform.shared.security.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.audit.application.service.CurrentActorProvider;
import org.zmy.observabilityplatform.audit.domain.model.AuditActor;
import reactor.core.publisher.Mono;

@Component
public class SpringSecurityCurrentActorProvider implements CurrentActorProvider {
    @Override
    public Mono<AuditActor> currentActor() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(authentication -> new AuditActor(authentication.getName(), authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(this::withoutRolePrefix)
                        .toList()));
    }

    private String withoutRolePrefix(String authority) {
        return authority.startsWith("ROLE_") ? authority.substring("ROLE_".length()) : authority;
    }
}
