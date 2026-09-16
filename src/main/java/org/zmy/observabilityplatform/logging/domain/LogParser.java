package org.zmy.observabilityplatform.logging.domain;

public interface LogParser {
    boolean supports(RawLogRecord record);

    ParsedLog parse(RawLogRecord record);
}
