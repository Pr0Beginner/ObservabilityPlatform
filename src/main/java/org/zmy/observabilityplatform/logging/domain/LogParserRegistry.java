package org.zmy.observabilityplatform.logging.domain;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LogParserRegistry {
    private final List<LogParser> parsers;

    public LogParserRegistry(List<LogParser> parsers) {
        this.parsers = parsers;
    }

    public ParsedLog parse(RawLogRecord record) {
        LogParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(record))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No log parser registered"));
        try {
            return parser.parse(record);
        } catch (IllegalArgumentException parseFailure) {
            return new ParsedLog(record.timestamp(), "UNKNOWN", record.content(), record.traceId(),
                    java.util.Map.of("parseError", parseFailure.getMessage()));
        }
    }
}
