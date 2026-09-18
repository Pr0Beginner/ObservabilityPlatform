package org.zmy.observabilityplatform.logging.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.command.InspectLogBatchCommand;
import org.zmy.observabilityplatform.incident.application.command.ObservedLogCommand;
import org.zmy.observabilityplatform.incident.application.service.ErrorThresholdDetector;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import org.zmy.observabilityplatform.logging.domain.repository.LogRepository;
import org.zmy.observabilityplatform.logging.domain.service.LogEntryFactory;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class LogProcessingService {
    private final LogEntryFactory logEntryFactory;
    private final LogRepository repository;
    private final ErrorThresholdDetector thresholdDetector;

    public LogProcessingService(LogEntryFactory logEntryFactory,
                                LogRepository repository,
                                ErrorThresholdDetector thresholdDetector) {
        this.logEntryFactory = logEntryFactory;
        this.repository = repository;
        this.thresholdDetector = thresholdDetector;
    }

    public Mono<Void> process(RawLogBatch batch) {
        // 先完成解析、脱敏和指纹计算；只对本次实际写入的日志执行事件检测，避免重试重复计数。
        List<LogEntry> entries = batch.getLogs().stream().map(raw -> logEntryFactory.create(batch, raw)).toList();
        return repository.saveAll(entries)
                .map(saved -> new InspectLogBatchCommand(saved.stream()
                        .map(entry -> new ObservedLogCommand(entry.getTimestamp(), entry.getService(),
                                entry.getEnvironment(), entry.getLevel(), entry.getFingerprint()))
                        .toList()))
                .flatMap(thresholdDetector::inspect);
    }
}
