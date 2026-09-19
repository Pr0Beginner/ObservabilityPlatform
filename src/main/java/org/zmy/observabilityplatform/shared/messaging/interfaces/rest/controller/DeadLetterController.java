package org.zmy.observabilityplatform.shared.messaging.interfaces.rest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.shared.messaging.application.service.DeadLetterService;
import org.zmy.observabilityplatform.shared.messaging.interfaces.rest.response.DeadLetterMessageResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/dead-letters")
public class DeadLetterController {
    private final DeadLetterService service;

    public DeadLetterController(DeadLetterService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<DeadLetterMessageResponse> findAll(@RequestParam(defaultValue = "100") int limit) {
        return service.findAll(limit).map(DeadLetterMessageResponse::from);
    }

    @PostMapping("/{id}/replay")
    public Mono<DeadLetterMessageResponse> replay(@PathVariable String id) {
        return service.replay(id).map(DeadLetterMessageResponse::from);
    }
}
