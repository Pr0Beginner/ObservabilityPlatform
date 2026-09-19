package org.zmy.observabilityplatform.shared.messaging.domain.repository;

import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeadLetterRepository {
    Mono<DeadLetterMessage> saveIfAbsent(DeadLetterMessage message);

    Mono<DeadLetterMessage> save(DeadLetterMessage message);

    Mono<DeadLetterMessage> findById(String id);

    Flux<DeadLetterMessage> findAll(int limit);
}
