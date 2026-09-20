package org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.shared.application.query.PageResult;
import org.zmy.observabilityplatform.shared.messaging.application.query.DeadLetterQueryRepository;
import org.zmy.observabilityplatform.shared.messaging.application.query.DeadLetterSearchQuery;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryDeadLetterRepository implements DeadLetterRepository, DeadLetterQueryRepository {
    private final Map<String, DeadLetterMessage> messages = new ConcurrentHashMap<>();
    private final Map<String, ReplayLease> replayLeases = new ConcurrentHashMap<>();

    @Override
    public Mono<DeadLetterMessage> saveIfAbsent(DeadLetterMessage message) {
        DeadLetterMessage existing = messages.putIfAbsent(message.getId(), message);
        return Mono.just(existing == null ? message : existing);
    }

    @Override
    public Mono<DeadLetterMessage> save(DeadLetterMessage message) {
        messages.put(message.getId(), message);
        if (message.getReplayedAt() != null) {
            replayLeases.remove(message.getId());
        }
        return Mono.just(message);
    }

    @Override
    public Mono<DeadLetterMessage> findById(String id) {
        return Mono.justOrEmpty(messages.get(id));
    }

    @Override
    public synchronized Mono<DeadLetterMessage> claimForReplay(String id, String owner,
                                                               Instant now, Instant leaseUntil) {
        DeadLetterMessage message = messages.get(id);
        if (message == null || message.getReplayedAt() != null) {
            return Mono.empty();
        }
        ReplayLease current = replayLeases.get(id);
        if (current != null && current.leaseUntil().isAfter(now)) {
            return Mono.empty();
        }
        replayLeases.put(id, new ReplayLease(owner, leaseUntil));
        return Mono.just(message);
    }

    @Override
    public synchronized Mono<DeadLetterMessage> completeReplay(String id, String owner, Instant replayedAt) {
        ReplayLease lease = replayLeases.get(id);
        DeadLetterMessage message = messages.get(id);
        if (lease == null || !lease.owner().equals(owner) || message == null || message.getReplayedAt() != null) {
            return Mono.empty();
        }
        DeadLetterMessage replayed = message.replayed(replayedAt);
        messages.put(id, replayed);
        replayLeases.remove(id);
        return Mono.just(replayed);
    }

    @Override
    public synchronized Mono<Boolean> releaseReplay(String id, String owner) {
        ReplayLease lease = replayLeases.get(id);
        if (lease == null || !lease.owner().equals(owner)) {
            return Mono.just(false);
        }
        replayLeases.remove(id);
        return Mono.just(true);
    }

    @Override
    public Mono<PageResult<DeadLetterMessage>> search(DeadLetterSearchQuery query) {
        Predicate<DeadLetterMessage> predicate = message -> matches(message, query);
        List<DeadLetterMessage> matches = messages.values().stream()
                .filter(predicate)
                .sorted(Comparator.comparing(DeadLetterMessage::getFailedAt).reversed()
                        .thenComparing(DeadLetterMessage::getId))
                .toList();
        List<DeadLetterMessage> pageItems = matches.stream()
                .skip(query.offset())
                .limit(query.getSize())
                .toList();
        return Mono.just(PageResult.of(pageItems, query.getPage(), query.getSize(), matches.size()));
    }

    private boolean matches(DeadLetterMessage message, DeadLetterSearchQuery query) {
        return sameIfPresent(query.getTopic(), message.getOriginalTopic())
                && (query.getStatus() == null || query.getStatus() == message.getStatus())
                && sameIfPresent(query.getFailureType(), message.getFailureType())
                && (query.getFailedFrom() == null || !message.getFailedAt().isBefore(query.getFailedFrom()))
                && (query.getFailedTo() == null || !message.getFailedAt().isAfter(query.getFailedTo()));
    }

    private boolean sameIfPresent(String expected, String actual) {
        return expected == null || expected.equalsIgnoreCase(actual);
    }

    @Override
    public Mono<Long> deleteReplayedBefore(Instant cutoff, int limit) {
        return deleteBefore(cutoff, null, limit, true);
    }

    @Override
    public Mono<Long> deleteUnreplayedBefore(Instant cutoff, Instant now, int limit) {
        return deleteBefore(cutoff, now, limit, false);
    }

    private Mono<Long> deleteBefore(Instant cutoff, Instant now, int limit, boolean replayed) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        long deleted = messages.entrySet().stream()
                .filter(entry -> (entry.getValue().getReplayedAt() != null) == replayed)
                .filter(entry -> replayed || !hasActiveLease(entry.getKey(), now))
                .filter(entry -> timestamp(entry.getValue(), replayed).isBefore(cutoff))
                .sorted(Comparator.comparing(entry -> timestamp(entry.getValue(), replayed)))
                .limit(limit)
                .filter(entry -> messages.remove(entry.getKey(), entry.getValue()))
                .count();
        return Mono.just(deleted);
    }

    private boolean hasActiveLease(String id, Instant now) {
        ReplayLease lease = replayLeases.get(id);
        return lease != null && lease.leaseUntil().isAfter(now);
    }

    private Instant timestamp(DeadLetterMessage message, boolean replayed) {
        return replayed ? message.getReplayedAt() : message.getFailedAt();
    }

    private static final class ReplayLease {
        private final String owner;
        private final Instant leaseUntil;

        private ReplayLease(String owner, Instant leaseUntil) {
            this.owner = owner;
            this.leaseUntil = leaseUntil;
        }

        private String owner() {
            return owner;
        }

        private Instant leaseUntil() {
            return leaseUntil;
        }
    }
}
