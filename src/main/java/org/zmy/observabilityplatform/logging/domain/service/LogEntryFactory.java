package org.zmy.observabilityplatform.logging.domain.service;

import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import org.zmy.observabilityplatform.logging.domain.model.ParsedLog;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class LogEntryFactory {
    private final LogParserRegistry parserRegistry;
    private final SensitiveDataProtector sensitiveDataProtector;
    private final LogFingerprintGenerator fingerprintGenerator;

    public LogEntryFactory(LogParserRegistry parserRegistry,
                           SensitiveDataProtector sensitiveDataProtector,
                           LogFingerprintGenerator fingerprintGenerator) {
        this.parserRegistry = parserRegistry;
        this.sensitiveDataProtector = sensitiveDataProtector;
        this.fingerprintGenerator = fingerprintGenerator;
    }

    public LogEntry create(RawLogBatch batch, RawLogRecord raw) {
        ParsedLog parsed = parserRegistry.parse(raw);
        Instant timestamp = parsed.getTimestamp() == null ? batch.getReceivedAt() : parsed.getTimestamp();
        String message = sensitiveDataProtector.protect(parsed.getMessage());
        String rawMessage = sensitiveDataProtector.protect(raw.getContent());
        String level = parsed.getLevel() == null ? "UNKNOWN" : parsed.getLevel().toUpperCase(Locale.ROOT);
        // 解析出的结构化属性和采集端附加属性都必须经过递归脱敏。
        Map<String, Object> attributes = new LinkedHashMap<>(sensitiveDataProtector.protectAttributes(parsed.getAttributes()));
        // 采集端属性后合并，使调用方显式传入的上下文拥有更高优先级。
        attributes.putAll(sensitiveDataProtector.protectAttributes(raw.getAttributes()));
        Boolean success = parsed.getSuccess();
        if (success == null && parsed.getStatusCode() != null) {
            success = parsed.getStatusCode() < 400;
        }
        return LogEntry.create(raw.getId(), batch.getBatchId(), timestamp, batch.getReceivedAt(),
                batch.getService(), batch.getEnvironment(), level, parsed.getTraceId(), parsed.getSpanId(),
                parsed.getParentSpanId(), parsed.getRequestId(), parsed.getOperation(), parsed.getSpanKind(),
                parsed.getStatusCode(), success, parsed.getErrorCode(), parsed.getDurationMs(), rawMessage, message,
                fingerprintGenerator.generate(batch.getService(), level, message), attributes);
    }

}
