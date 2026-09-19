package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyDetectionSettings;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.model.IncidentType;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.model.MetricType;
import org.zmy.observabilityplatform.incident.domain.model.MetricWindow;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import org.zmy.observabilityplatform.incident.domain.repository.MetricWindowRepository;
import org.zmy.observabilityplatform.incident.domain.repository.MetricTraceSampleRepository;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentTraceLinkRepository;
import org.zmy.observabilityplatform.incident.domain.repository.AnomalyPolicyRepository;
import org.zmy.observabilityplatform.incident.domain.model.IncidentTraceLink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AnomalyEvaluationService {
    private final MetricWindowRepository metricRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentLifecycleService lifecycleService;
    private final MetricTraceSampleRepository traceSampleRepository;
    private final IncidentTraceLinkRepository traceLinkRepository;
    private final AnomalyPolicyRepository policyRepository;
    private final AnomalyPolicyResolver policyResolver;
    private final Clock clock;

    public AnomalyEvaluationService(
            MetricWindowRepository metricRepository,
            IncidentRepository incidentRepository,
            IncidentLifecycleService lifecycleService,
            MetricTraceSampleRepository traceSampleRepository,
            IncidentTraceLinkRepository traceLinkRepository,
            AnomalyPolicyRepository policyRepository,
            AnomalyPolicyResolver policyResolver,
            Clock clock) {
        this.metricRepository = metricRepository;
        this.incidentRepository = incidentRepository;
        this.lifecycleService = lifecycleService;
        this.traceSampleRepository = traceSampleRepository;
        this.traceLinkRepository = traceLinkRepository;
        this.policyRepository = policyRepository;
        this.policyResolver = policyResolver;
        this.clock = clock;
    }

    public Mono<Void> evaluate(Instant windowStart) {
        return Mono.zip(metricRepository.findByWindow(windowStart).collectList(),
                        incidentRepository.findAll().filter(this::isRecoverable).collectList(),
                        loadBaselines(windowStart))
                .flatMapMany(tuple -> evaluateRequestMetrics(
                        windowStart, index(tuple.getT1()), tuple.getT3(), tuple.getT2()))
                .then();
    }

    public Mono<Void> evaluateRepeatedErrors(Instant windowStart) {
        return Mono.zip(metricRepository.findByWindow(windowStart)
                                .filter(metric -> metric.getKey().getType() == MetricType.ERROR_FINGERPRINT)
                                .collectMap(metric -> metric.getKey().value()),
                        incidentRepository.findAll()
                                .filter(incident -> incident.getType() == IncidentType.REPEATED_ERROR)
                                .filter(this::isRecoverable).collectList())
                .flatMapMany(tuple -> {
                    Map<String, MetricWindow> current = tuple.getT1();
                    Set<MetricKey> keys = new LinkedHashSet<>();
                    current.values().forEach(metric -> keys.add(metric.getKey()));
                    tuple.getT2().forEach(incident -> keys.add(MetricKey.errorFingerprint(
                            incident.getService(), incident.getEnvironment(), incident.getFingerprint())));
                    return Flux.fromIterable(keys)
                            .concatMap(key -> evaluateRepeatedError(windowStart, key,
                                    count(current, key)));
                })
                .then();
    }

    private Mono<Incident> evaluateRepeatedError(Instant windowStart, MetricKey key, long count) {
        String dedupKey = String.join("|", IncidentType.REPEATED_ERROR.name(), key.getService(),
                key.getEnvironment(), key.getDimension());
        return policyResolver.resolve(key.getService(), key.getEnvironment(), null)
                .filter(AnomalyPolicy::isEnabled)
                .flatMap(policy -> {
                    boolean anomalous = policy.getSettings().isRepeatedError(count);
                    return incidentRepository.findByDedupKey(dedupKey)
                            .flatMap(existing -> anomalous
                                    ? lifecycleService.update(existing.getId(), current -> current.observeAnomaly(
                                            count, null, windowStart, clock.instant(), policy.reference()))
                                    : isRecoverable(existing)
                                            ? lifecycleService.update(existing.getId(), current ->
                                                    current.registerHealthyWindow(
                                                            policy.getSettings().getRecoveryWindows(), windowStart,
                                                            clock.instant(), (double) count, null, policy.reference()))
                                            : Mono.just(existing))
                            .switchIfEmpty(Mono.defer(() -> anomalous
                                    ? lifecycleService.open(Incident.open(UUID.randomUUID().toString(), dedupKey,
                                            key.getService(), key.getEnvironment(), key.getDimension(), "ERROR",
                                            count, windowStart, clock.instant(), policy.reference()))
                                    : Mono.empty()))
                            .flatMap(incident -> anomalous
                                    ? traceSampleRepository.findTraceId(key, windowStart)
                                            .flatMap(traceId -> traceLinkRepository.link(new IncidentTraceLink(
                                                    incident.getId(), traceId, clock.instant())))
                                            .thenReturn(incident)
                                    : Mono.just(incident));
                });
    }

    private Flux<Incident> evaluateRequestMetrics(Instant windowStart, Map<String, MetricWindow> current,
                                                   Map<Integer, Map<String, MetricWindow>> baselines,
                                                   Iterable<Incident> activeIncidents) {
        Set<MetricKey> totalKeys = keysOfType(current, baselines.values(), MetricType.REQUEST_TOTAL);
        for (Incident incident : activeIncidents) {
            if (incident.getType() == IncidentType.REQUEST_VOLUME_SPIKE
                    || incident.getType() == IncidentType.REQUEST_VOLUME_DROP
                    || incident.getType() == IncidentType.NO_TRAFFIC
                    || incident.getType() == IncidentType.FAILURE_RATE_SPIKE) {
                totalKeys.add(MetricKey.requestTotal(incident.getService(), incident.getEnvironment(),
                        incident.getOperation()));
            }
        }
        Flux<Incident> totals = Flux.fromIterable(totalKeys)
                .concatMap(total -> evaluateScope(windowStart, total, current, baselines));
        Set<MetricKey> errorKeys = keysOfType(current, baselines.values(), MetricType.ERROR_CODE);
        for (Incident incident : activeIncidents) {
            if ((incident.getType() == IncidentType.ERROR_CODE_COUNT_SPIKE
                    || incident.getType() == IncidentType.ERROR_CODE_RATE_SPIKE)
                    && incident.getDimension() != null) {
                errorKeys.add(MetricKey.errorCode(incident.getService(), incident.getEnvironment(),
                        incident.getOperation(), incident.getDimension()));
            }
        }
        Flux<Incident> errors = Flux.fromIterable(errorKeys)
                .concatMap(error -> evaluateErrorCode(windowStart, error, current, baselines));
        return Flux.concat(totals, errors);
    }

    private Flux<Incident> evaluateScope(Instant windowStart, MetricKey total,
                                         Map<String, MetricWindow> current,
                                         Map<Integer, Map<String, MetricWindow>> baselines) {
        return policyResolver.resolve(total.getService(), total.getEnvironment(), total.getOperation())
                .filter(AnomalyPolicy::isEnabled)
                .flatMapMany(policy -> {
                    Map<String, MetricWindow> baseline = baselineFor(policy, baselines);
                    AnomalyDetectionSettings settings = policy.getSettings();
                    long currentTotal = count(current, total);
                    long baselineTotal = count(baseline, total);
                    MetricKey failure = MetricKey.requestFailure(
                            total.getService(), total.getEnvironment(), total.getOperation());
                    long currentFailures = count(current, failure);
                    long baselineFailures = count(baseline, failure);
                    double currentFailureRate = settings.rate(currentFailures, currentTotal);
                    double baselineFailureRate = settings.rate(baselineFailures, baselineTotal);
                    return Flux.concat(
                            apply(windowStart, IncidentType.NO_TRAFFIC, total, total, null, currentTotal,
                                    (double) baselineTotal, settings.isNoTraffic(currentTotal, baselineTotal), policy),
                            apply(windowStart, IncidentType.REQUEST_VOLUME_SPIKE, total, total, null, currentTotal,
                                    (double) baselineTotal,
                                    settings.isRequestVolumeSpike(currentTotal, baselineTotal), policy),
                            apply(windowStart, IncidentType.REQUEST_VOLUME_DROP, total, total, null, currentTotal,
                                    (double) baselineTotal,
                                    settings.isRequestVolumeDrop(currentTotal, baselineTotal), policy),
                            apply(windowStart, IncidentType.FAILURE_RATE_SPIKE, total, failure, null,
                                    currentFailureRate, baselineFailureRate,
                                    settings.isFailureRateSpike(currentFailures, currentTotal,
                                            baselineFailures, baselineTotal), policy));
                });
    }

    private Flux<Incident> evaluateErrorCode(Instant windowStart, MetricKey error,
                                             Map<String, MetricWindow> current,
                                             Map<Integer, Map<String, MetricWindow>> baselines) {
        return policyResolver.resolve(error.getService(), error.getEnvironment(), error.getOperation())
                .filter(AnomalyPolicy::isEnabled)
                .flatMapMany(policy -> {
                    Map<String, MetricWindow> baseline = baselineFor(policy, baselines);
                    AnomalyDetectionSettings settings = policy.getSettings();
                    MetricKey total = MetricKey.requestTotal(
                            error.getService(), error.getEnvironment(), error.getOperation());
                    long currentTotal = count(current, total);
                    long baselineTotal = count(baseline, total);
                    long currentErrors = count(current, error);
                    long baselineErrors = count(baseline, error);
                    double currentRate = settings.rate(currentErrors, currentTotal);
                    double baselineRate = settings.rate(baselineErrors, baselineTotal);
                    return Flux.concat(
                            apply(windowStart, IncidentType.ERROR_CODE_COUNT_SPIKE, error, error,
                                    error.getDimension(), currentErrors, (double) baselineErrors,
                                    settings.isErrorCodeCountSpike(currentErrors, baselineErrors), policy),
                            apply(windowStart, IncidentType.ERROR_CODE_RATE_SPIKE, error, error,
                                    error.getDimension(), currentRate, baselineRate,
                                    settings.isErrorCodeRateSpike(currentErrors, currentTotal,
                                            baselineErrors, baselineTotal), policy));
                });
    }

    private Mono<Incident> apply(Instant windowStart, IncidentType type, MetricKey metric, MetricKey sampleMetric,
                                 String dimension,
                                 double currentValue, Double baselineValue, boolean anomalous,
                                 AnomalyPolicy policy) {
        String dedupKey = dedupKey(type, metric, dimension);
        return incidentRepository.findByDedupKey(dedupKey)
                .flatMap(existing -> anomalous
                        ? lifecycleService.update(existing.getId(), current -> current.observeAnomaly(
                                currentValue, baselineValue, windowStart, clock.instant(), policy.reference()))
                        : isRecoverable(existing)
                                ? lifecycleService.update(existing.getId(), current -> current.registerHealthyWindow(
                                        policy.getSettings().getRecoveryWindows(), windowStart, clock.instant(),
                                        currentValue, baselineValue, policy.reference()))
                                : Mono.just(existing))
                .switchIfEmpty(Mono.defer(() -> anomalous
                        ? lifecycleService.open(Incident.openAnomaly(UUID.randomUUID().toString(), dedupKey,
                                type, title(type, metric, dimension), metric.getService(), metric.getEnvironment(),
                                metric.getOperation(), dimension, currentValue, baselineValue, windowStart,
                                clock.instant(), policy.reference()))
                        : Mono.empty()))
                .flatMap(incident -> anomalous
                        ? traceSampleRepository.findTraceId(sampleMetric, windowStart)
                                .flatMap(traceId -> traceLinkRepository.link(new IncidentTraceLink(
                                        incident.getId(), traceId, clock.instant())))
                                .thenReturn(incident)
                        : Mono.just(incident));
    }

    private Mono<Map<Integer, Map<String, MetricWindow>>> loadBaselines(Instant windowStart) {
        return policyRepository.findAll()
                .filter(AnomalyPolicy::isEnabled)
                .map(policy -> policy.getSettings().getComparisonPeriodMinutes())
                .distinct()
                .concatMap(period -> metricRepository.findByWindow(windowStart.minus(Duration.ofMinutes(period)))
                        .collectList()
                        .map(metrics -> Map.entry(period, index(metrics))))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, LinkedHashMap::new);
    }

    private Map<String, MetricWindow> baselineFor(AnomalyPolicy policy,
                                                   Map<Integer, Map<String, MetricWindow>> baselines) {
        return baselines.getOrDefault(policy.getSettings().getComparisonPeriodMinutes(), Map.of());
    }

    private boolean isRecoverable(Incident incident) {
        return incident.getStatus() != IncidentStatus.RESOLVED && incident.getStatus() != IncidentStatus.CLOSED;
    }

    private Map<String, MetricWindow> index(Iterable<MetricWindow> metrics) {
        Map<String, MetricWindow> indexed = new LinkedHashMap<>();
        metrics.forEach(metric -> indexed.put(metric.getKey().value(), metric));
        return indexed;
    }

    private Set<MetricKey> keysOfType(Map<String, MetricWindow> current,
                                      Iterable<Map<String, MetricWindow>> baselines,
                                      MetricType type) {
        Set<MetricKey> keys = new LinkedHashSet<>();
        current.values().stream().map(MetricWindow::getKey).filter(key -> key.getType() == type).forEach(keys::add);
        baselines.forEach(baseline -> baseline.values().stream().map(MetricWindow::getKey)
                .filter(key -> key.getType() == type).forEach(keys::add));
        return keys;
    }

    private long count(Map<String, MetricWindow> metrics, MetricKey key) {
        MetricWindow metric = metrics.get(key.value());
        return metric == null ? 0 : metric.getCount();
    }

    private String dedupKey(IncidentType type, MetricKey metric, String dimension) {
        return String.join("|", type.name(), metric.getService(), metric.getEnvironment(),
                metric.getOperation(), dimension == null ? "*" : dimension);
    }

    private String title(IncidentType type, MetricKey metric, String dimension) {
        return switch (type) {
            case REQUEST_VOLUME_SPIKE -> "Request volume increased for " + metric.getOperation();
            case REQUEST_VOLUME_DROP -> "Request volume dropped for " + metric.getOperation();
            case NO_TRAFFIC -> "No request traffic for " + metric.getOperation();
            case ERROR_CODE_COUNT_SPIKE -> "Error code " + dimension + " count increased for "
                    + metric.getOperation();
            case ERROR_CODE_RATE_SPIKE -> "Error code " + dimension + " rate increased for "
                    + metric.getOperation();
            case FAILURE_RATE_SPIKE -> "Request failure rate increased for " + metric.getOperation();
            case REPEATED_ERROR -> "Repeated errors in " + metric.getService();
        };
    }
}
