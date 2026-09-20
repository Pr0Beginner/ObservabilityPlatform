package org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterReplayAttempt;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterReplayAttemptRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryDeadLetterReplayAttemptRepository implements DeadLetterReplayAttemptRepository {
    private final Map<String, DeadLetterReplayAttempt> attempts = new ConcurrentHashMap<>();

    @Override
    public Mono<DeadLetterReplayAttempt> save(DeadLetterReplayAttempt attempt) {
        attempts.put(attempt.getId(), attempt);
        return Mono.just(attempt);
    }

    @Override
    public Flux<DeadLetterReplayAttempt> findByDeadLetterId(String deadLetterId, int limit) {
        return Flux.fromStream(attempts.values().stream()
                .filter(attempt -> attempt.getDeadLetterId().equals(deadLetterId))
                .sorted(Comparator.comparing(DeadLetterReplayAttempt::getStartedAt).reversed()
                        .thenComparing(DeadLetterReplayAttempt::getId))
                .limit(limit));
    }
}
