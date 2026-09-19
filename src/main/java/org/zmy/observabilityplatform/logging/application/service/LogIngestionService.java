package org.zmy.observabilityplatform.logging.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.logging.application.command.IngestLogBatchCommand;
import org.zmy.observabilityplatform.logging.application.command.IngestLogItemCommand;
import org.zmy.observabilityplatform.logging.application.dto.LogIngestionResult;
import org.zmy.observabilityplatform.logging.application.publisher.RawLogBatchPublisher;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class LogIngestionService {
    private final RawLogBatchPublisher eventPublisher;
    private final int maxBatchSize;
    private final Clock clock;

    public LogIngestionService(RawLogBatchPublisher eventPublisher,
                               @Value("${app.ingestion.max-batch-size:1000}") int maxBatchSize,
                               Clock clock) {
        this.eventPublisher = eventPublisher;
        this.maxBatchSize = maxBatchSize;
        this.clock = clock;
    }

    public Mono<LogIngestionResult> ingest(IngestLogBatchCommand command) {
        // 在进入异步处理链路前拦截无效批次，避免发布无法处理的消息。
        if (command.getLogs() == null || command.getLogs().isEmpty()) {
            return Mono.error(new IllegalArgumentException("The log batch must not be empty"));
        }
        if (command.getLogs().size() > maxBatchSize) {
            return Mono.error(new IllegalArgumentException("Batch exceeds maximum size of " + maxBatchSize));
        }
        RawLogBatch batch = toDomain(command);
        return eventPublisher.publish(batch)
                .thenReturn(new LogIngestionResult(batch.getBatchId(), batch.size(), "ACCEPTED"));
    }

    private RawLogBatch toDomain(IngestLogBatchCommand command) {
        Instant receivedAt = clock.instant();
        // 整个批次共享接收时间，确保缺少原始时间的日志仍能保持一致的时间基准。
        List<RawLogRecord> records = IntStream.range(0, command.getLogs().size())
                .mapToObj(index -> toDomain(command.getBatchId(), index, receivedAt, command.getLogs().get(index)))
                .toList();
        return RawLogBatch.receive(command.getBatchId(), command.getService(), command.getEnvironment(),
                receivedAt, records);
    }

    private RawLogRecord toDomain(String batchId, int index, Instant receivedAt, IngestLogItemCommand item) {
        // 稳定 ID 让同一批次的重试命中相同记录，由存储层完成幂等去重。
        Instant timestamp = item.getTimestamp() == null ? receivedAt : item.getTimestamp();
        return RawLogRecord.capture(batchId, index, timestamp, item.getContent(), item.getFormat(),
                item.getTraceId(), item.getSpanId(), item.getParentSpanId(), item.getRequestId(),
                item.getOperation(), item.getSpanKind(), item.getStatusCode(), item.getSuccess(),
                item.getErrorCode(), item.getDurationMs(), item.getAttributes());
    }
}
