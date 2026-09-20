package org.zmy.observabilityplatform.audit.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.audit.application.query.AuditQueryRepository;
import org.zmy.observabilityplatform.audit.application.query.AuditSearchQuery;
import org.zmy.observabilityplatform.audit.domain.model.AuditRecord;
import org.zmy.observabilityplatform.shared.application.query.PageResult;
import reactor.core.publisher.Mono;

@Service
public class AuditQueryService {
    private final AuditQueryRepository repository;

    public AuditQueryService(AuditQueryRepository repository) {
        this.repository = repository;
    }

    public Mono<PageResult<AuditRecord>> search(AuditSearchQuery query) {
        return repository.search(query);
    }
}
