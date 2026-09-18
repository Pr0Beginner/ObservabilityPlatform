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
        Map<String, Object> attributes = protectAttributes(parsed.getAttributes());
        // 采集端属性后合并，使调用方显式传入的上下文拥有更高优先级。
        attributes.putAll(protectAttributes(raw.getAttributes()));
        return LogEntry.create(raw.getId(), batch.getBatchId(), timestamp, batch.getReceivedAt(),
                batch.getService(), batch.getEnvironment(), level, parsed.getTraceId(), rawMessage, message,
                fingerprintGenerator.generate(batch.getService(), level, message), attributes);
    }

    private Map<String, Object> protectAttributes(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> result.put(key, protectAttributeValue(key, value)));
        }
        return result;
    }

    private Object protectAttributeValue(String key, Object value) {
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        if (normalizedKey.contains("password") || normalizedKey.contains("passwd")
                || normalizedKey.contains("token") || normalizedKey.contains("authorization")
                || normalizedKey.contains("cookie")) {
            return "***";
        }
        if (value instanceof String text) {
            return sensitiveDataProtector.protect(text);
        }
        // 嵌套对象和集合也可能携带敏感字段，需保留原结构逐层处理。
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> converted = new LinkedHashMap<>();
            nested.forEach((nestedKey, nestedValue) -> converted.put(String.valueOf(nestedKey),
                    protectAttributeValue(String.valueOf(nestedKey), nestedValue)));
            return converted;
        }
        if (value instanceof Iterable<?> values) {
            return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                    .map(item -> protectAttributeValue(key, item))
                    .toList();
        }
        return value;
    }
}
