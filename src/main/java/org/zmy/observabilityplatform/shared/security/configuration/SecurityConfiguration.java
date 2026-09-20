package org.zmy.observabilityplatform.shared.security.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.zmy.observabilityplatform.shared.security.interfaces.RestSecurityErrorHandler;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public MapReactiveUserDetailsService reactiveUserDetailsService(SecurityProperties properties,
                                                                     PasswordEncoder passwordEncoder) {
        return new MapReactiveUserDetailsService(
                user(properties.getReadOnly(), SecurityRoles.READ_ONLY, passwordEncoder),
                user(properties.getOperator(), SecurityRoles.OPERATOR, passwordEncoder),
                user(properties.getAdmin(), SecurityRoles.ADMIN, passwordEncoder));
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                          SecurityProperties properties,
                                                          RestSecurityErrorHandler errorHandler) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        if (!properties.isEnabled()) {
            return http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                    .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                    .build();
        }
        return http.httpBasic(basic -> basic.authenticationEntryPoint(errorHandler))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/prometheus", "/api/v1/audit-records/**")
                        .hasRole(SecurityRoles.ADMIN)
                        .pathMatchers(HttpMethod.POST, "/api/v1/anomaly-policies")
                        .hasRole(SecurityRoles.ADMIN)
                        .pathMatchers(HttpMethod.PUT, "/api/v1/anomaly-policies/**")
                        .hasRole(SecurityRoles.ADMIN)
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/incidents/**")
                        .hasAnyRole(SecurityRoles.OPERATOR, SecurityRoles.ADMIN)
                        .pathMatchers(HttpMethod.POST, "/api/v1/incidents/*/diagnoses",
                                "/api/v1/diagnoses/*/cancel", "/api/v1/diagnoses/*/retry",
                                "/api/v1/dead-letters/*/replay", "/api/v1/logs/batch")
                        .hasAnyRole(SecurityRoles.OPERATOR, SecurityRoles.ADMIN)
                        .pathMatchers(HttpMethod.GET, "/api/v1/**")
                        .hasAnyRole(SecurityRoles.READ_ONLY, SecurityRoles.OPERATOR, SecurityRoles.ADMIN)
                        .pathMatchers("/actuator/**").hasRole(SecurityRoles.ADMIN)
                        .anyExchange().authenticated())
                .build();
    }

    private UserDetails user(SecurityProperties.UserAccount account, String role,
                             PasswordEncoder passwordEncoder) {
        if (account.getUsername() == null || account.getUsername().isBlank()
                || account.getPassword() == null || account.getPassword().isBlank()) {
            throw new IllegalArgumentException("Security user name and password must not be blank");
        }
        return User.withUsername(account.getUsername())
                .password(passwordEncoder.encode(account.getPassword()))
                .roles(role)
                .build();
    }
}
