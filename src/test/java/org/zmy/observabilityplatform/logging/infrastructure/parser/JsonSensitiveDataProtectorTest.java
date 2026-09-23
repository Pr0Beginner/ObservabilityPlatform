package org.zmy.observabilityplatform.logging.infrastructure.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;
import org.zmy.observabilityplatform.logging.domain.service.LogEntryFactory;
import org.zmy.observabilityplatform.logging.domain.service.LogFingerprintGenerator;
import org.zmy.observabilityplatform.logging.domain.service.LogParserRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSensitiveDataProtectorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSensitiveDataProtector protector = new JsonSensitiveDataProtector(mapper);

    @Test
    void protectsNestedJsonArraysAndEmbeddedEscapedMessagesWithoutBreakingJson() throws Exception {
        String raw = mapper.writeValueAsString(Map.of(
                "password", "root-secret-value",
                "nested", List.of(Map.of("api_key", "api-secret-value", "count", 2)),
                "message", mapper.writeValueAsString(Map.of("Authorization", "Bearer bearer-value"))));

        String protectedJson = protector.protect(raw);

        assertThat(protectedJson).doesNotContain("root-secret-value", "api-secret-value", "bearer-value");
        assertThat(mapper.readTree(protectedJson).path("nested").get(0).path("count").asInt()).isEqualTo(2);
        assertThat(mapper.readTree(mapper.readTree(protectedJson).path("message").asText())
                .path("Authorization").asText()).isEqualTo("***");
    }

    @Test
    void protectsQuotedTextValuesAndJsonFragments() {
        String result = protector.protect("request {\"password\":\"space and \\\"quoted\\\" value\"} "
                + "api_key='key with spaces' token=Bearer bearer-value");
        assertThat(result).doesNotContain("space and", "quoted", "key with spaces", "bearer-value");
    }

    @Test
    void logFactoryProtectsEveryStoredRepresentation() throws Exception {
        String secret = "synthetic-private-value";
        String raw = mapper.writeValueAsString(Map.of("message", "{\"password\":\"" + secret + "\"}",
                "level", "ERROR", "password", secret, "api_key", secret));
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        RawLogRecord record = RawLogRecord.capture("batch", 0, now, raw, "JSON", "trace",
                Map.of("nested", List.of(Map.of("accessToken", secret)), "cookie", "session=" + secret));
        RawLogBatch batch = RawLogBatch.receive("batch", "orders", "test", now, List.of(record));
        LogEntryFactory factory = new LogEntryFactory(new LogParserRegistry(List.of(new JsonLogParser(mapper))),
                protector, new LogFingerprintGenerator());

        var entry = factory.create(batch, record);

        assertThat(entry.getRawMessage()).doesNotContain(secret);
        assertThat(entry.getMessage()).doesNotContain(secret);
        assertThat(mapper.writeValueAsString(entry.getAttributes())).doesNotContain(secret);
    }
}
