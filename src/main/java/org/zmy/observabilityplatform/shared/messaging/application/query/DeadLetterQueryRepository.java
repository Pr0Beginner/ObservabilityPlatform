package org.zmy.observabilityplatform.shared.messaging.application.query;

import org.zmy.observabilityplatform.shared.application.query.PageResult;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import reactor.core.publisher.Mono;

public interface DeadLetterQueryRepository {
    Mono<PageResult<DeadLetterMessage>> search(DeadLetterSearchQuery query);
}
