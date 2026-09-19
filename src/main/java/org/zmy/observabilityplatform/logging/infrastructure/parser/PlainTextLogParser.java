package org.zmy.observabilityplatform.logging.infrastructure.parser;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.logging.domain.model.ParsedLog;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;
import org.zmy.observabilityplatform.logging.domain.service.LogParser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(100)
public class PlainTextLogParser implements LogParser {
    private static final Pattern LEVEL = Pattern.compile("(?i)\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b");

    @Override
    public boolean supports(RawLogRecord record) {
        return true;
    }

    @Override
    public ParsedLog parse(RawLogRecord record) {
        String content = record.getContent() == null ? "" : record.getContent();
        Matcher matcher = LEVEL.matcher(content);
        String level = matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "INFO";
        if ("WARNING".equals(level)) {
            level = "WARN";
        }
        return ParsedLog.parsed(record.getTimestamp(), level, content, record.getTraceId(), record.getSpanId(),
                record.getParentSpanId(), record.getRequestId(), record.getOperation(), record.getSpanKind(),
                record.getStatusCode(), record.getSuccess(), record.getErrorCode(), record.getDurationMs(),
                new LinkedHashMap<>());
    }
}
