package org.zmy.observabilityplatform.logging.domain.repository;

import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import reactor.core.publisher.Mono;

import java.util.List;

public interface LogRepository {
    Mono<List<LogEntry>> saveAll(List<LogEntry> entries);

}
