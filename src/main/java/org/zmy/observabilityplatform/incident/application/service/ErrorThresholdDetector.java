package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.command.InspectLogBatchCommand;
import org.zmy.observabilityplatform.incident.application.command.ObservedLogCommand;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import org.zmy.observabilityplatform.incident.domain.service.ErrorIncidentPolicy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ErrorThresholdDetector {
    private final IncidentRepository incidentRepository;
    private final ErrorIncidentPolicy incidentPolicy;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicLong> windows = new ConcurrentHashMap<>();

    public ErrorThresholdDetector(IncidentRepository incidentRepository,
                                  @Value("${app.incident.error-threshold:3}") int threshold,
                                  Clock clock) {
        this.incidentRepository = incidentRepository;
        this.incidentPolicy = new ErrorIncidentPolicy(threshold);
        this.clock = clock;
    }

    public Mono<Void> inspect(InspectLogBatchCommand command) {
        // 仅错误级别日志参与告警计数，同批次按顺序处理以保证窗口计数稳定。
        pruneExpiredWindows();
        return Flux.fromIterable(command.getLogs())
                .filter(entry -> incidentPolicy.observes(entry.getLevel()))
                .concatMap(this::inspectOne)
                .then();
    }

    private Mono<Void> inspectOne(ObservedLogCommand entry) {
        // 同一服务、环境和指纹在每分钟窗口内聚合为一个事件。
        Instant window = incidentPolicy.windowOf(entry.getTimestamp());
        String dedupKey = incidentPolicy.dedupKey(entry.getService(), entry.getEnvironment(),
                entry.getFingerprint(), window);
        long count = windows.computeIfAbsent(dedupKey, ignored -> new AtomicLong()).incrementAndGet();
        if (!incidentPolicy.hasReachedThreshold(count)) {
            return Mono.empty();
        }
        // 达到阈值后更新已有事件；首次达到阈值时创建，避免同类错误形成事件风暴。
        return incidentRepository.findByDedupKey(dedupKey)
                .flatMap(existing -> incidentRepository.save(existing.registerOccurrences(count, clock.instant())))
                .switchIfEmpty(Mono.defer(() -> incidentRepository.save(incidentPolicy.openIncident(
                        UUID.randomUUID().toString(),
                        dedupKey,
                        entry.getService(),
                        entry.getEnvironment(),
                        entry.getFingerprint(),
                        entry.getLevel(),
                        count,
                        window,
                        clock.instant()))))
                .then();
    }

    private void pruneExpiredWindows() {
        // 小规模窗口无需频繁清理，超过容量后再移除过期或损坏的键。
        if (windows.size() < 1_000) {
            return;
        }
        Instant cutoff = clock.instant().minus(5, ChronoUnit.MINUTES);
        windows.keySet().removeIf(key -> {
            int separator = key.lastIndexOf('|');
            if (separator < 0) {
                return true;
            }
            try {
                return Instant.parse(key.substring(separator + 1)).isBefore(cutoff);
            } catch (RuntimeException invalidKey) {
                return true;
            }
        });
    }
}
