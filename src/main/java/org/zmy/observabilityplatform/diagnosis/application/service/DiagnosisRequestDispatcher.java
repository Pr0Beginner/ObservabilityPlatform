package org.zmy.observabilityplatform.diagnosis.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.diagnosis.application.publisher.DiagnosisRequestOutbox;
import org.zmy.observabilityplatform.diagnosis.application.publisher.DiagnosisRequestedPublisher;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class DiagnosisRequestDispatcher {
    private final DiagnosisRequestOutbox outbox;
    private final DiagnosisRequestedPublisher publisher;

    public DiagnosisRequestDispatcher(DiagnosisRequestOutbox outbox, DiagnosisRequestedPublisher publisher) {
        this.outbox = outbox;
        this.publisher = publisher;
    }

    public Mono<Void> dispatch(int limit) {
        // A crash after delivery can resend the same event; Agent deduplicates taskId + version.
        return outbox.pending(Math.max(1, Math.min(limit, 100)))
                .concatMap(event -> Mono.defer(() -> publisher.publish(event))
                        .then(Mono.defer(() -> outbox.acknowledge(event.getEventId())))
                        .onErrorResume(error -> {
                            log.warn("Diagnosis request delivery will retry: eventId={}", event.getEventId(), error);
                            return Mono.empty();
                        }))
                .then();
    }
}
