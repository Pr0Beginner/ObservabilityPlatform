package org.zmy.observabilityplatform.logging.domain.service;

import org.zmy.observabilityplatform.logging.domain.model.ParsedLog;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;

import java.util.List;

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
            // 单条日志解析失败不应中断整个批次，保留原文并记录失败原因供后续检索。
            return ParsedLog.unparsed(record, java.util.Map.of("parseError", parseFailure.getMessage()));
        }
    }
}
