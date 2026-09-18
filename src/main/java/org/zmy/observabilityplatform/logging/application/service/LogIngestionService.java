package org.zmy.observabilityplatform.logging.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.logging.application.command.IngestLogBatchCommand;
import org.zmy.observabilityplatform.logging.application.command.IngestLogItemCommand;
import org.zmy.observabilityplatform.logging.application.dto.LogIngestionResult;
import org.zmy.observabilityplatform.logging.application.publisher.RawLogBatchPublisher;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import org.zmy.observabilityplatform.logging.domain.model.RawLogFormat;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
        if (command.logs() == null || command.logs().isEmpty()) {
            return Mono.error(new IllegalArgumentException("The log batch must not be empty"));
        }
        if (command.logs().size() > maxBatchSize) {
            return Mono.error(new IllegalArgumentException("Batch exceeds maximum size of " + maxBatchSize));
        }
        RawLogBatch batch = toDomain(command);
        return eventPublisher.publish(batch)
                .thenReturn(new LogIngestionResult(batch.batchId(), batch.logs().size(), "ACCEPTED"));
    }

    private RawLogBatch toDomain(IngestLogBatchCommand command) {
        Instant receivedAt = clock.instant();
        // 整个批次共享接收时间，确保缺少原始时间的日志仍能保持一致的时间基准。
        List<RawLogRecord> records = IntStream.range(0, command.logs().size())
                .mapToObj(index -> toDomain(command.batchId(), index, receivedAt, command.logs().get(index)))
                .toList();
        return new RawLogBatch(command.batchId(), command.service(), command.environment(), receivedAt, records);
    }

    private RawLogRecord toDomain(String batchId, int index, Instant receivedAt, IngestLogItemCommand item) {
        // 稳定 ID 让同一批次的重试命中相同记录，由存储层完成幂等去重。
        String key = batchId + ":" + index;
        String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        RawLogFormat format;
        try {
            format = RawLogFormat.valueOf(item.format().toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalidFormat) {
            throw new IllegalArgumentException("Unsupported log format: " + item.format(), invalidFormat);
        }
        return new RawLogRecord(id, item.timestamp() == null ? receivedAt : item.timestamp(), item.content(), format,
                item.traceId(), item.attributes() == null ? Map.of() : new LinkedHashMap<>(item.attributes()));
    }
}
