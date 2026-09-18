package org.zmy.observabilityplatform.logging.application.publisher;

import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import reactor.core.publisher.Mono;

public interface RawLogBatchPublisher {
    Mono<Void> publish(RawLogBatch batch);
}
