package org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryDeadLetterRepository implements DeadLetterRepository {
    private final Map<String, DeadLetterMessage> messages = new ConcurrentHashMap<>();

    @Override
    public Mono<DeadLetterMessage> saveIfAbsent(DeadLetterMessage message) {
        DeadLetterMessage existing = messages.putIfAbsent(message.getId(), message);
        return Mono.just(existing == null ? message : existing);
    }

    @Override
    public Mono<DeadLetterMessage> save(DeadLetterMessage message) {
        messages.put(message.getId(), message);
        return Mono.just(message);
    }

    @Override
    public Mono<DeadLetterMessage> findById(String id) {
        return Mono.justOrEmpty(messages.get(id));
    }

    @Override
    public Flux<DeadLetterMessage> findAll(int limit) {
        return Flux.fromStream(messages.values().stream()
                .sorted(Comparator.comparing(DeadLetterMessage::getFailedAt).reversed())
                .limit(limit));
    }
}
