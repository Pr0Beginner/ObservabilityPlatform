package org.zmy.observabilityplatform.logging.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.command.InspectLogBatchCommand;
import org.zmy.observabilityplatform.incident.application.command.ObservedLogCommand;
import org.zmy.observabilityplatform.incident.application.service.ErrorThresholdDetector;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import org.zmy.observabilityplatform.logging.domain.model.ParsedLog;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;
import org.zmy.observabilityplatform.logging.domain.repository.LogRepository;
import org.zmy.observabilityplatform.logging.domain.service.LogFingerprintGenerator;
import org.zmy.observabilityplatform.logging.domain.service.LogParserRegistry;
import org.zmy.observabilityplatform.logging.domain.service.SensitiveDataProtector;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class LogProcessingService {
    private final LogParserRegistry parserRegistry;
    private final SensitiveDataProtector sensitiveDataProtector;
    private final LogFingerprintGenerator fingerprintGenerator;
    private final LogRepository repository;
    private final ErrorThresholdDetector thresholdDetector;

    public LogProcessingService(LogParserRegistry parserRegistry,
                                SensitiveDataProtector sensitiveDataProtector,
                                LogFingerprintGenerator fingerprintGenerator,
                                LogRepository repository,
                                ErrorThresholdDetector thresholdDetector) {
        this.parserRegistry = parserRegistry;
        this.sensitiveDataProtector = sensitiveDataProtector;
        this.fingerprintGenerator = fingerprintGenerator;
        this.repository = repository;
        this.thresholdDetector = thresholdDetector;
    }

    public Mono<Void> process(RawLogBatch batch) {
        List<LogEntry> entries = batch.logs().stream().map(raw -> toEntry(batch, raw)).toList();
        return repository.saveAll(entries)
                .map(saved -> new InspectLogBatchCommand(saved.stream()
                        .map(entry -> new ObservedLogCommand(entry.timestamp(), entry.service(), entry.environment(),
                                entry.level(), entry.fingerprint()))
                        .toList()))
                .flatMap(thresholdDetector::inspect);
    }

    private LogEntry toEntry(RawLogBatch batch, RawLogRecord raw) {
        ParsedLog parsed = parserRegistry.parse(raw);
        Instant timestamp = parsed.timestamp() == null ? batch.receivedAt() : parsed.timestamp();
        String message = sensitiveDataProtector.protect(parsed.message());
        String rawMessage = sensitiveDataProtector.protect(raw.content());
        String level = parsed.level() == null ? "UNKNOWN" : parsed.level().toUpperCase();
        Map<String, Object> attributes = protectAttributes(parsed.attributes());
        if (raw.attributes() != null) {
            attributes.putAll(protectAttributes(raw.attributes()));
        }
        return new LogEntry(
                raw.id(), batch.batchId(), timestamp, batch.receivedAt(), batch.service(), batch.environment(),
                level, parsed.traceId(), rawMessage, message,
                fingerprintGenerator.generate(batch.service(), level, message), attributes);
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
