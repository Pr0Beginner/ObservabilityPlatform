package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.command.InspectLogBatchCommand;
import org.zmy.observabilityplatform.incident.application.command.ObservedLogCommand;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentSeverity;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ErrorThresholdDetector {
    private final IncidentRepository incidentRepository;
    private final int threshold;
    private final ConcurrentHashMap<String, AtomicLong> windows = new ConcurrentHashMap<>();

    public ErrorThresholdDetector(IncidentRepository incidentRepository,
                                  @Value("${app.incident.error-threshold:3}") int threshold) {
        this.incidentRepository = incidentRepository;
        this.threshold = threshold;
    }

    public Mono<Void> inspect(InspectLogBatchCommand command) {
        // 仅错误级别日志参与告警计数，同批次按顺序处理以保证窗口计数稳定。
        pruneExpiredWindows();
        return Flux.fromIterable(command.logs())
                .filter(entry -> "ERROR".equals(entry.level()) || "FATAL".equals(entry.level()))
                .concatMap(this::inspectOne)
                .then();
    }

    private Mono<Void> inspectOne(ObservedLogCommand entry) {
        // 同一服务、环境和指纹在每分钟窗口内聚合为一个事件。
        Instant window = entry.timestamp().truncatedTo(ChronoUnit.MINUTES);
        String dedupKey = String.join("|", entry.service(), entry.environment(), entry.fingerprint(), window.toString());
        long count = windows.computeIfAbsent(dedupKey, ignored -> new AtomicLong()).incrementAndGet();
        if (count < threshold) {
            return Mono.empty();
        }
        // 达到阈值后更新已有事件；首次达到阈值时创建，避免同类错误形成事件风暴。
        return incidentRepository.findByDedupKey(dedupKey)
                .flatMap(existing -> incidentRepository.save(existing.withErrorCount(count)))
                .switchIfEmpty(Mono.defer(() -> incidentRepository.save(new Incident(
                        UUID.randomUUID().toString(),
                        dedupKey,
                        "Repeated " + entry.level() + " logs in " + entry.service(),
                        entry.service(),
                        entry.environment(),
                        entry.fingerprint(),
                        IncidentSeverity.P2,
                        IncidentStatus.OPEN,
                        window,
                        Instant.now(),
                        count,
                        "unassigned",
                        null))))
                .then();
    }

    private void pruneExpiredWindows() {
        // 小规模窗口无需频繁清理，超过容量后再移除过期或损坏的键。
        if (windows.size() < 1_000) {
            return;
        }
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
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
