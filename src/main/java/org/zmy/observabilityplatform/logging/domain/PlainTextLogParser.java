package org.zmy.observabilityplatform.logging.domain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
        String content = record.content() == null ? "" : record.content();
        Matcher matcher = LEVEL.matcher(content);
        String level = matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "INFO";
        if ("WARNING".equals(level)) {
            level = "WARN";
        }
        return new ParsedLog(record.timestamp(), level, content, record.traceId(), new LinkedHashMap<>());
    }
}
