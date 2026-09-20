package org.zmy.observabilityplatform.shared.messaging.domain.repository;

import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface DeadLetterRepository {
    Mono<DeadLetterMessage> saveIfAbsent(DeadLetterMessage message);

    Mono<DeadLetterMessage> save(DeadLetterMessage message);

    Mono<DeadLetterMessage> findById(String id);

    Mono<DeadLetterMessage> claimForReplay(String id, String owner, Instant now, Instant leaseUntil);

    Mono<DeadLetterMessage> completeReplay(String id, String owner, Instant replayedAt);

    Mono<Boolean> releaseReplay(String id, String owner);

    Mono<Long> deleteReplayedBefore(Instant cutoff, int limit);

    Mono<Long> deleteUnreplayedBefore(Instant cutoff, Instant now, int limit);
}
