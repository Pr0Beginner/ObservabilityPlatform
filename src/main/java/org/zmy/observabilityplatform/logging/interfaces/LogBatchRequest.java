package org.zmy.observabilityplatform.logging.interfaces;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.zmy.observabilityplatform.logging.domain.RawLogBatch;
import org.zmy.observabilityplatform.logging.domain.RawLogFormat;
import org.zmy.observabilityplatform.logging.domain.RawLogRecord;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

public record LogBatchRequest(
        @NotBlank String batchId,
        @NotBlank String service,
        @NotBlank String environment,
        @NotEmpty @Size(max = 1000) List<@Valid LogItemRequest> logs) {

    public RawLogBatch toDomain() {
        Instant receivedAt = Instant.now();
        List<RawLogRecord> records = IntStream.range(0, logs.size())
                .mapToObj(index -> logs.get(index).toDomain(batchId, index, receivedAt))
                .toList();
        return new RawLogBatch(batchId, service, environment, receivedAt, records);
    }

    public record LogItemRequest(
            Instant timestamp,
            @NotBlank String content,
            @NotNull RawLogFormat format,
            String traceId,
            Map<String, Object> attributes) {

        RawLogRecord toDomain(String batchId, int index, Instant receivedAt) {
            String key = batchId + ":" + index;
            String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
            return new RawLogRecord(id, timestamp == null ? receivedAt : timestamp, content, format,
                    traceId, attributes == null ? Map.of() : attributes);
        }
    }
}
