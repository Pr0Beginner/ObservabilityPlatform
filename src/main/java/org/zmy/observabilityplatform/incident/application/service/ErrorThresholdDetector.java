package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.command.InspectLogBatchCommand;
import org.zmy.observabilityplatform.incident.application.command.ObservedLogCommand;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyReference;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.repository.MetricWindowRepository;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentTraceLinkRepository;
import org.zmy.observabilityplatform.incident.domain.model.IncidentTraceLink;
import org.zmy.observabilityplatform.incident.domain.repository.MetricTraceSampleRepository;
import org.zmy.observabilityplatform.incident.domain.service.ErrorIncidentPolicy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ErrorThresholdDetector {
    private final IncidentRepository incidentRepository;
    private final IncidentLifecycleService lifecycleService;
    private final AnomalyPolicyResolver policyResolver;
    private final Clock clock;
    private final MetricWindowRepository metricWindowRepository;
    private final IncidentTraceLinkRepository traceLinkRepository;
    private final MetricTraceSampleRepository traceSampleRepository;

    public ErrorThresholdDetector(IncidentRepository incidentRepository,
                                  MetricWindowRepository metricWindowRepository,
                                  IncidentLifecycleService lifecycleService,
                                  IncidentTraceLinkRepository traceLinkRepository,
                                  MetricTraceSampleRepository traceSampleRepository,
                                  AnomalyPolicyResolver policyResolver,
                                  Clock clock) {
        this.incidentRepository = incidentRepository;
        this.metricWindowRepository = metricWindowRepository;
        this.lifecycleService = lifecycleService;
        this.traceLinkRepository = traceLinkRepository;
        this.traceSampleRepository = traceSampleRepository;
        this.policyResolver = policyResolver;
        this.clock = clock;
    }

    public Mono<Void> inspect(InspectLogBatchCommand command) {
        Map<String, List<ObservedLogCommand>> groups = command.getLogs().stream()
                .filter(entry -> observes(entry.getLevel()))
                .collect(java.util.stream.Collectors.groupingBy(
                        entry -> entry.getService() + "\u0000" + entry.getEnvironment(),
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return Flux.fromIterable(groups.values())
                .concatMap(entries -> {
                    ObservedLogCommand first = entries.get(0);
                    return policyResolver.resolve(first.getService(), first.getEnvironment(), null)
                            .filter(policy -> policy.isEnabled())
                            .flatMapMany(policy -> {
                                ErrorIncidentPolicy incidentPolicy = new ErrorIncidentPolicy(
                                        policy.getSettings().getErrorThreshold());
                                return Flux.fromIterable(entries)
                                        .concatMap(entry -> inspectOne(entry, incidentPolicy, policy.reference()));
                            });
                })
                .then();
    }

    private Mono<Void> inspectOne(ObservedLogCommand entry, ErrorIncidentPolicy incidentPolicy,
                                  AnomalyPolicyReference policy) {
        // 同一服务、环境和指纹在每分钟窗口内聚合为一个事件。
        Instant window = incidentPolicy.windowOf(entry.getTimestamp());
        String dedupKey = incidentPolicy.dedupKey(entry.getService(), entry.getEnvironment(),
                entry.getFingerprint());
        MetricKey metricKey = MetricKey.errorFingerprint(entry.getService(), entry.getEnvironment(),
                entry.getFingerprint());
        return metricWindowRepository.increment(metricKey, window, 1)
                .flatMap(metric -> traceSampleRepository.recordIfAbsent(metricKey, window, entry.getTraceId())
                        .thenReturn(metric))
                .filter(metric -> incidentPolicy.hasReachedThreshold(metric.getCount()))
                .flatMap(metric -> incidentRepository.findByDedupKey(dedupKey)
                        .flatMap(existing -> lifecycleService.update(existing.getId(), current ->
                                current.registerOccurrences(metric.getCount(), window, clock.instant(), policy)))
                        .switchIfEmpty(Mono.defer(() -> lifecycleService.open(incidentPolicy.openIncident(
                                UUID.randomUUID().toString(), dedupKey, entry.getService(), entry.getEnvironment(),
                                entry.getFingerprint(), entry.getLevel(), metric.getCount(), window,
                                clock.instant(), policy)))))
                .flatMap(incident -> linkTrace(incident.getId(), entry.getTraceId()).thenReturn(incident))
                .then();
    }

    private boolean observes(String level) {
        return "ERROR".equals(level) || "FATAL".equals(level);
    }

    private Mono<Void> linkTrace(String incidentId, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return Mono.empty();
        }
        return traceLinkRepository.link(new IncidentTraceLink(incidentId, traceId, clock.instant()));
    }
}
