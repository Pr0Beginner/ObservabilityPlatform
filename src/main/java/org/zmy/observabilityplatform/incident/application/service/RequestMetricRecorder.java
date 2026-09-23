package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.command.InspectLogBatchCommand;
import org.zmy.observabilityplatform.incident.application.command.ObservedLogCommand;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.repository.MetricTraceSampleRepository;
import org.zmy.observabilityplatform.incident.domain.repository.MetricWindowRepository;
import org.zmy.observabilityplatform.incident.domain.service.MetricWindowPolicy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class RequestMetricRecorder {
    private final MetricWindowRepository repository;
    private final MetricWindowPolicy windowPolicy;
    private final MetricTraceSampleRepository traceSampleRepository;

    public RequestMetricRecorder(MetricWindowRepository repository,
                                 MetricTraceSampleRepository traceSampleRepository,
                                 @Value("${app.incident.anomaly.window-minutes:5}") int windowMinutes) {
        this.repository = repository;
        this.traceSampleRepository = traceSampleRepository;
        this.windowPolicy = new MetricWindowPolicy(windowMinutes);
    }

    public Mono<Void> record(InspectLogBatchCommand command) {
        return Flux.fromIterable(command.getLogs())
                .filter(this::isCompletedRequest)
                .concatMap(this::recordOne)
                .then();
    }

    private Mono<Void> recordOne(ObservedLogCommand log) {
        Instant window = windowPolicy.windowOf(log.getTimestamp());
        MetricKey total = MetricKey.requestTotal(log.getService(), log.getEnvironment(), log.getOperation());
        List<MetricKey> metrics = new ArrayList<>();
        metrics.add(total);
        MetricKey failure = null;
        if (isFailed(log)) {
            failure = MetricKey.requestFailure(log.getService(), log.getEnvironment(), log.getOperation());
            metrics.add(failure);
        }
        MetricKey error = null;
        if (log.getErrorCode() != null && !log.getErrorCode().isBlank()) {
            error = MetricKey.errorCode(log.getService(), log.getEnvironment(),
                    log.getOperation(), log.getErrorCode());
            metrics.add(error);
        }
        Mono<Void> samples = traceSampleRepository.recordIfAbsent(total, window, log.getTraceId());
        if (failure != null) {
            samples = samples.then(traceSampleRepository.recordIfAbsent(failure, window, log.getTraceId()));
        }
        if (error != null) {
            samples = samples.then(traceSampleRepository.recordIfAbsent(error, window, log.getTraceId()));
        }
        return repository.recordOnce("request:" + log.getLogId(), metrics, window).then(samples);
    }

    private boolean isCompletedRequest(ObservedLogCommand log) {
        if (log.getOperation() == null || log.getOperation().isBlank()) {
            return false;
        }
        boolean hasOutcome = log.getSuccess() != null || log.getStatusCode() != null;
        boolean completionSignal = log.getDurationMs() != null || "SERVER".equalsIgnoreCase(log.getSpanKind());
        return hasOutcome && completionSignal;
    }

    private boolean isFailed(ObservedLogCommand log) {
        return Boolean.FALSE.equals(log.getSuccess())
                || log.getStatusCode() != null && log.getStatusCode() >= 500;
    }
}
