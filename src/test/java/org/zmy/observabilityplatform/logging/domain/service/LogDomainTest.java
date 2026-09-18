package org.zmy.observabilityplatform.logging.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogDomainTest {
    private final SensitiveDataProtector protector = new SensitiveDataProtector();
    private final LogFingerprintGenerator fingerprints = new LogFingerprintGenerator();

    @Test
    void masksCommonSecrets() {
        String value = protector.protect("phone=13812345678 password=hunter2 token=Bearer abc.def");

        assertThat(value).doesNotContain("13812345678", "hunter2", "abc.def");
    }

    @Test
    void normalizesVolatileValuesForFingerprinting() {
        String first = fingerprints.generate("orders", "ERROR", "timeout id=123 host=10.1.2.3");
        String second = fingerprints.generate("orders", "ERROR", "timeout id=456 host=10.9.8.7");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void protectsLogBatchInvariantsAndCollectionOwnership() {
        Instant timestamp = Instant.parse("2026-09-18T08:30:00Z");
        RawLogRecord record = RawLogRecord.capture("batch-1", 0, timestamp, "INFO started", "TEXT",
                null, Map.of());
        List<RawLogRecord> source = new ArrayList<>(List.of(record));
        RawLogBatch batch = RawLogBatch.receive("batch-1", "orders", "test", timestamp, source);

        source.clear();

        assertThat(batch.getLogs()).containsExactly(record);
        assertThatThrownBy(() -> batch.getLogs().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> RawLogBatch.receive("batch-2", "orders", "test", timestamp, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializesAndRestoresKafkaBatchPayload() throws Exception {
        Instant timestamp = Instant.parse("2026-09-18T08:30:00Z");
        RawLogRecord record = RawLogRecord.capture("batch-1", 0, timestamp, "INFO started", "TEXT",
                "trace-1", Map.of("source", "test"));
        RawLogBatch batch = RawLogBatch.receive("batch-1", "orders", "test", timestamp, List.of(record));
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

        RawLogBatch restored = objectMapper.readValue(objectMapper.writeValueAsString(batch), RawLogBatch.class);

        assertThat(restored).isEqualTo(batch);
    }
}
