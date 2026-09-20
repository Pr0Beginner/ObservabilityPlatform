package org.zmy.observabilityplatform.audit.domain.repository;

import org.zmy.observabilityplatform.audit.domain.model.AuditRecord;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface AuditRepository {
    Mono<AuditRecord> save(AuditRecord record);

    Mono<Long> deleteBefore(Instant cutoff, int limit);
}
