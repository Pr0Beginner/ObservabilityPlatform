package org.zmy.observabilityplatform.logging.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.logging.application.query.LogQueryRepository;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import org.zmy.observabilityplatform.logging.domain.model.TraceCallTree;
import org.zmy.observabilityplatform.logging.domain.service.TraceReconstructionService;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class TraceQueryService {
    private static final int MAX_LOGS = 2_000;

    private final LogQueryRepository repository;
    private final TraceReconstructionService reconstructionService;

    public TraceQueryService(LogQueryRepository repository, TraceReconstructionService reconstructionService) {
        this.repository = repository;
        this.reconstructionService = reconstructionService;
    }

    public Mono<TraceCallTree> findById(String traceId, int requestedLimit) {
        if (traceId == null || traceId.isBlank()) {
            return Mono.error(new IllegalArgumentException("traceId must not be blank"));
        }
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LOGS));
        return repository.findByTraceId(traceId, limit + 1)
                .collectList()
                .flatMap(logs -> reconstruct(traceId, logs, limit));
    }

    public Flux<TraceCallTree> findByRequestId(String requestId, int requestedTraceLimit, int requestedLogLimit) {
        if (requestId == null || requestId.isBlank()) {
            return Flux.error(new IllegalArgumentException("requestId must not be blank"));
        }
        int traceLimit = Math.max(1, Math.min(requestedTraceLimit, 20));
        int logLimit = Math.max(1, Math.min(requestedLogLimit, MAX_LOGS));
        return repository.findByRequestId(requestId, MAX_LOGS)
                .map(LogEntry::getTraceId)
                .filter(traceId -> traceId != null && !traceId.isBlank())
                .distinct().take(traceLimit)
                .concatMap(traceId -> findById(traceId, logLimit));
    }

    private Mono<TraceCallTree> reconstruct(String traceId, List<LogEntry> logs, int limit) {
        if (logs.isEmpty()) {
            return Mono.error(new NotFoundException("Trace not found: " + traceId));
        }
        boolean truncated = logs.size() > limit;
        List<LogEntry> selected = truncated ? logs.subList(0, limit) : logs;
        return Mono.just(reconstructionService.reconstruct(traceId, selected, truncated));
    }
}
