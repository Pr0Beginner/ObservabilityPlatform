package org.zmy.observabilityplatform.incident.interfaces.rest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.incident.application.service.TraceIncidentQueryService;
import org.zmy.observabilityplatform.incident.interfaces.rest.response.IncidentResponse;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/traces")
public class TraceIncidentController {
    private final TraceIncidentQueryService service;

    public TraceIncidentController(TraceIncidentQueryService service) {
        this.service = service;
    }

    @GetMapping("/{traceId}/incidents")
    public Flux<IncidentResponse> findIncidents(@PathVariable String traceId,
                                                @RequestParam(defaultValue = "20") int limit) {
        return service.findByTraceId(traceId, limit).map(IncidentResponse::from);
    }
}
