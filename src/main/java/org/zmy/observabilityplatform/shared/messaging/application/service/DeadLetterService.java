package org.zmy.observabilityplatform.shared.messaging.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import org.zmy.observabilityplatform.shared.messaging.application.publisher.DeadLetterReplayPublisher;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;

@Service
public class DeadLetterService {
    private final DeadLetterRepository repository;
    private final DeadLetterReplayPublisher publisher;
    private final Clock clock;

    public DeadLetterService(DeadLetterRepository repository, DeadLetterReplayPublisher publisher, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    public Flux<DeadLetterMessage> findAll(int requestedLimit) {
        return repository.findAll(Math.max(1, Math.min(requestedLimit, 200)));
    }

    public Mono<DeadLetterMessage> replay(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Dead-letter message not found: " + id)))
                .flatMap(message -> publisher.publish(message)
                        .then(repository.save(message.replayed(clock.instant()))));
    }
}
