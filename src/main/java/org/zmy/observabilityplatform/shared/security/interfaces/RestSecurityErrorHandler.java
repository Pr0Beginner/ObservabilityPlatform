package org.zmy.observabilityplatform.shared.security.interfaces;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.zmy.observabilityplatform.audit.application.service.AuditTrailService;
import org.zmy.observabilityplatform.shared.interfaces.rest.ApiErrorCode;
import org.zmy.observabilityplatform.shared.interfaces.rest.ApiExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class RestSecurityErrorHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {
    private final AuditTrailService auditTrailService;
    private final SecurityAuditOperationResolver operationResolver;
    private final ObjectMapper objectMapper;

    public RestSecurityErrorHandler(AuditTrailService auditTrailService,
                                    SecurityAuditOperationResolver operationResolver,
                                    ObjectMapper objectMapper) {
        this.auditTrailService = auditTrailService;
        this.operationResolver = operationResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"observability-platform\"");
        return reject(exchange, ApiErrorCode.AUTHENTICATION_REQUIRED,
                "Authentication is required", "Authentication is missing or invalid");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange,
                             org.springframework.security.access.AccessDeniedException exception) {
        return reject(exchange, ApiErrorCode.ACCESS_DENIED,
                "Access is denied", "The authenticated actor does not have the required role");
    }

    private Mono<Void> reject(ServerWebExchange exchange, ApiErrorCode code,
                              String responseMessage, String auditReason) {
        return auditTrailService.recordDenied(operationResolver.resolve(exchange), auditReason)
                .then(Mono.defer(() -> write(exchange.getResponse(), code, responseMessage)));
    }

    private Mono<Void> write(ServerHttpResponse response, ApiErrorCode code, String message) {
        response.setStatusCode(code.getHttpStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ApiExceptionHandler.ApiError error = new ApiExceptionHandler.ApiError(
                code.name(), message, code.isRetryable(), Instant.now(), List.of());
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(error);
        } catch (JsonProcessingException exception) {
            body = ("{\"code\":\"" + code.name() + "\",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
