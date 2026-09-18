package org.zmy.observabilityplatform.logging.domain.service;

import org.zmy.observabilityplatform.logging.domain.model.ParsedLog;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;

public interface LogParser {
    boolean supports(RawLogRecord record);

    ParsedLog parse(RawLogRecord record);
}
