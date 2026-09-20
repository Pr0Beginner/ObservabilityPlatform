package org.zmy.observabilityplatform.shared.messaging.domain.repository;

import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterReplayAttempt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeadLetterReplayAttemptRepository {
    Mono<DeadLetterReplayAttempt> save(DeadLetterReplayAttempt attempt);

    Flux<DeadLetterReplayAttempt> findByDeadLetterId(String deadLetterId, int limit);
}
