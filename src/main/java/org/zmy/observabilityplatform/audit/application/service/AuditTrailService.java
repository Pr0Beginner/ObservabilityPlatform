package org.zmy.observabilityplatform.audit.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.audit.domain.model.AuditActor;
import org.zmy.observabilityplatform.audit.domain.model.AuditOutcome;
import org.zmy.observabilityplatform.audit.domain.model.AuditRecord;
import org.zmy.observabilityplatform.audit.domain.repository.AuditRepository;
import org.zmy.observabilityplatform.shared.tracing.TraceContext;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Service
public class AuditTrailService {
    private static final String UNKNOWN_TRACE_ID = "unknown";
    private final AuditRepository repository;
    private final CurrentActorProvider actorProvider;
    private final Clock clock;

    public AuditTrailService(AuditRepository repository, CurrentActorProvider actorProvider, Clock clock) {
        this.repository = repository;
        this.actorProvider = actorProvider;
        this.clock = clock;
    }

    public <T> Mono<T> audit(AuditOperation operation, Supplier<Mono<T>> execution,
                             Function<T, AuditResult> resultMapper) {
        return Mono.deferContextual(context -> actorProvider.currentActor()
                .defaultIfEmpty(AuditActor.anonymous())
                .flatMap(actor -> Mono.defer(execution)
                        .flatMap(result -> {
                            AuditResult auditResult;
                            try {
                                auditResult = Objects.requireNonNull(resultMapper.apply(result),
                                        "resultMapper must not return null");
                            } catch (RuntimeException error) {
                                log.error("Failed to build audit state after a successful operation {}",
                                        operation.getAction(), error);
                                return Mono.just(result);
                            }
                            String targetId = hasText(auditResult.getTargetId())
                                    ? auditResult.getTargetId() : operation.getTargetId();
                            return persist(actor, operation, targetId, AuditOutcome.SUCCEEDED,
                                    traceId(context.getOrDefault(TraceContext.class, null)),
                                    auditResult.getBeforeState(), auditResult.getAfterState(), null)
                                    .thenReturn(result);
                        })
                        .onErrorResume(error -> persist(actor, operation, operation.getTargetId(),
                                        AuditOutcome.FAILED,
                                        traceId(context.getOrDefault(TraceContext.class, null)),
                                        Map.of(), Map.of(), reason(error))
                                .then(Mono.error(error)))));
    }

    public Mono<Void> recordDenied(AuditOperation operation, String reason) {
        return Mono.deferContextual(context -> actorProvider.currentActor()
                .defaultIfEmpty(AuditActor.anonymous())
                .flatMap(actor -> persist(actor, operation, operation.getTargetId(), AuditOutcome.DENIED,
                        traceId(context.getOrDefault(TraceContext.class, null)), Map.of(), Map.of(), reason)));
    }

    private Mono<Void> persist(AuditActor actor, AuditOperation operation, String targetId,
                               AuditOutcome outcome, String traceId, Map<String, Object> beforeState,
                               Map<String, Object> afterState, String failureReason) {
        // 审计不可反向改变已完成的业务结果，否则客户端重试可能造成重复操作。
        return Mono.defer(() -> {
            AuditRecord record = AuditRecord.create(UUID.randomUUID().toString(), actor,
                    operation.getAction(), operation.getTargetType(), targetId, outcome, traceId,
                    clock.instant(), beforeState, afterState, failureReason);
            return repository.save(record).then()
                    .onErrorResume(error -> {
                        log.error("Failed to persist audit record {}", record.getId(), error);
                        return Mono.empty();
                    });
        }).onErrorResume(error -> {
            log.error("Failed to construct an audit record for operation {}", operation.getAction(), error);
            return Mono.empty();
        });
    }

    private String traceId(TraceContext context) {
        return context == null ? UNKNOWN_TRACE_ID : context.getTraceId();
    }

    private String reason(Throwable error) {
        return hasText(error.getMessage()) ? error.getMessage() : error.getClass().getName();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
