package org.zmy.observabilityplatform.audit.application.query;

import org.zmy.observabilityplatform.audit.domain.model.AuditRecord;
import org.zmy.observabilityplatform.shared.application.query.PageResult;
import reactor.core.publisher.Mono;

public interface AuditQueryRepository {
    Mono<PageResult<AuditRecord>> search(AuditSearchQuery query);
}
