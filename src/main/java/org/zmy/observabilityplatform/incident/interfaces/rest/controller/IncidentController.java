package org.zmy.observabilityplatform.incident.interfaces.rest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.incident.application.service.IncidentQueryService;
import org.zmy.observabilityplatform.incident.interfaces.rest.response.IncidentResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {
    private final IncidentQueryService service;

    public IncidentController(IncidentQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<IncidentResponse> findAll() {
        return service.findAll().map(IncidentResponse::from);
    }

    @GetMapping("/{incidentId}")
    public Mono<IncidentResponse> findById(@PathVariable String incidentId) {
        return service.findById(incidentId).map(IncidentResponse::from);
    }
}
