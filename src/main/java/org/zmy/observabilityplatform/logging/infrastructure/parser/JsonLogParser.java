package org.zmy.observabilityplatform.logging.infrastructure.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.logging.domain.model.ParsedLog;
import org.zmy.observabilityplatform.logging.domain.model.RawLogFormat;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;
import org.zmy.observabilityplatform.logging.domain.service.LogParser;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(10)
public class JsonLogParser implements LogParser {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final ObjectMapper objectMapper;

    public JsonLogParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(RawLogRecord record) {
        if (record.getFormat() == RawLogFormat.JSON) {
            return true;
        }
        String value = record.getContent() == null ? "" : record.getContent().trim();
        return record.getFormat() == RawLogFormat.AUTO && value.startsWith("{") && value.endsWith("}");
    }

    @Override
    public ParsedLog parse(RawLogRecord record) {
        try {
            JsonNode node = objectMapper.readTree(record.getContent());
            if (!node.isObject()) {
                throw new IllegalArgumentException("JSON log must be an object");
            }
            Map<String, Object> attributes = new LinkedHashMap<>(objectMapper.convertValue(node, MAP_TYPE));
            // 兼容常见日志框架的字段命名，缺失时回退到采集阶段提供的值。
            Instant timestamp = parseInstant(firstText(node, "timestamp", "@timestamp", "time"),
                    record.getTimestamp());
            String level = normalizeLevel(firstText(node, "level", "severity", "logLevel"));
            String message = firstText(node, "message", "msg", "error");
            String traceId = firstText(node, "traceId", "trace_id", "trace.id");
            String spanId = firstText(node, "spanId", "span_id", "span.id");
            String parentSpanId = firstText(node, "parentSpanId", "parent_span_id", "parent.span.id");
            String requestId = firstText(node, "requestId", "request_id", "http.request.id");
            String operation = firstText(node, "operation", "route", "http.route");
            String spanKind = firstText(node, "spanKind", "span_kind", "span.kind");
            Integer statusCode = firstInteger(node, "statusCode", "status_code", "http.status_code");
            Boolean success = firstBoolean(node, "success");
            String errorCode = firstText(node, "errorCode", "error_code", "error.code");
            Long durationMs = firstLong(node, "durationMs", "duration_ms", "http.duration_ms");
            return ParsedLog.parsed(timestamp, level, message == null ? record.getContent() : message,
                    fallback(traceId, record.getTraceId()), fallback(spanId, record.getSpanId()),
                    fallback(parentSpanId, record.getParentSpanId()), fallback(requestId, record.getRequestId()),
                    fallback(operation, record.getOperation()), fallback(spanKind, record.getSpanKind()),
                    statusCode == null ? record.getStatusCode() : statusCode,
                    success == null ? record.getSuccess() : success,
                    fallback(errorCode, record.getErrorCode()),
                    durationMs == null ? record.getDurationMs() : durationMs, attributes);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON log", exception);
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private Integer firstInteger(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.intValue();
            }
        }
        return null;
    }

    private Long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToLong()) {
                return value.longValue();
            }
        }
        return null;
    }

    private Boolean firstBoolean(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isBoolean()) {
                return value.booleanValue();
            }
        }
        return null;
    }

    private String fallback(String parsed, String provided) {
        return parsed == null || parsed.isBlank() ? provided : parsed;
    }

    private Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private String normalizeLevel(String level) {
        return level == null || level.isBlank() ? "INFO" : level.toUpperCase();
    }
}
