package org.zmy.observabilityplatform.shared.messaging.interfaces.rest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.shared.messaging.application.query.DeadLetterSearchQuery;
import org.zmy.observabilityplatform.shared.messaging.application.service.DeadLetterService;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterStatus;
import org.zmy.observabilityplatform.shared.messaging.interfaces.rest.response.DeadLetterMessageResponse;
import org.zmy.observabilityplatform.shared.messaging.interfaces.rest.response.DeadLetterReplayAttemptResponse;
import org.zmy.observabilityplatform.shared.interfaces.rest.PageResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/dead-letters")
public class DeadLetterController {
    private final DeadLetterService service;

    public DeadLetterController(DeadLetterService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<PageResponse<DeadLetterMessageResponse>> search(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) DeadLetterStatus status,
            @RequestParam(required = false) String failureType,
            @RequestParam(required = false) Instant failedFrom,
            @RequestParam(required = false) Instant failedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        DeadLetterSearchQuery query = new DeadLetterSearchQuery(topic, status, failureType,
                failedFrom, failedTo, page, size);
        return service.search(query).map(result -> PageResponse.from(result, DeadLetterMessageResponse::from));
    }

    @PostMapping("/{id}/replay")
    public Mono<DeadLetterMessageResponse> replay(@PathVariable String id) {
        return service.replay(id).map(DeadLetterMessageResponse::from);
    }

    @GetMapping("/{id}/replay-attempts")
    public Flux<DeadLetterReplayAttemptResponse> findReplayAttempts(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int limit) {
        return service.findReplayAttempts(id, limit).map(DeadLetterReplayAttemptResponse::from);
    }
}
