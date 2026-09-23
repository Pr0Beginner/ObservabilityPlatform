package org.zmy.observabilityplatform.logging.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.command.InspectLogBatchCommand;
import org.zmy.observabilityplatform.incident.application.command.ObservedLogCommand;
import org.zmy.observabilityplatform.incident.application.service.ErrorThresholdDetector;
import org.zmy.observabilityplatform.incident.application.service.RequestMetricRecorder;
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
    private final RequestMetricRecorder requestMetricRecorder;

    public LogProcessingService(LogEntryFactory logEntryFactory,
                                LogRepository repository,
                                ErrorThresholdDetector thresholdDetector,
                                RequestMetricRecorder requestMetricRecorder) {
        this.logEntryFactory = logEntryFactory;
        this.repository = repository;
        this.thresholdDetector = thresholdDetector;
        this.requestMetricRecorder = requestMetricRecorder;
    }

    public Mono<Void> process(RawLogBatch batch) {
        // 索引成功只代表日志可查询。所有记录继续参与业务处理，指标仓储按日志 ID 独立幂等。
        List<LogEntry> entries = batch.getLogs().stream().map(raw -> logEntryFactory.create(batch, raw)).toList();
        return repository.saveAll(entries)
                .then(Mono.fromSupplier(() -> new InspectLogBatchCommand(entries.stream()
                        .map(entry -> new ObservedLogCommand(entry.getId(), entry.getTimestamp(), entry.getService(),
                                entry.getEnvironment(), entry.getLevel(), entry.getFingerprint(),
                                entry.getTraceId(), entry.getRequestId(), entry.getOperation(), entry.getSpanKind(),
                                entry.getStatusCode(), entry.getSuccess(), entry.getErrorCode(), entry.getDurationMs()))
                        .toList())))
                .flatMap(command -> Mono.whenDelayError(
                        thresholdDetector.inspect(command), requestMetricRecorder.record(command)));
    }
}
