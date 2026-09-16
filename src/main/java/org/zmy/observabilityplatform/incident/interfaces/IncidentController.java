package org.zmy.observabilityplatform.incident.interfaces;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.incident.application.IncidentQueryService;
import org.zmy.observabilityplatform.incident.domain.Incident;
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
    public Flux<Incident> findAll() {
        return service.findAll();
    }

    @GetMapping("/{incidentId}")
    public Mono<Incident> findById(@PathVariable String incidentId) {
        return service.findById(incidentId);
    }
}
