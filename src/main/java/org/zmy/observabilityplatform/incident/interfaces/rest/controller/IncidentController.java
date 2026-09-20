package org.zmy.observabilityplatform.incident.interfaces.rest.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.incident.application.query.IncidentSearchQuery;
import org.zmy.observabilityplatform.incident.application.service.IncidentManagementService;
import org.zmy.observabilityplatform.incident.application.service.IncidentNotificationService;
import org.zmy.observabilityplatform.incident.application.service.IncidentQueryService;
import org.zmy.observabilityplatform.incident.application.service.IncidentTraceQueryService;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.model.IncidentType;
import org.zmy.observabilityplatform.incident.interfaces.rest.request.AssignIncidentRequest;
import org.zmy.observabilityplatform.incident.interfaces.rest.request.TransitionIncidentRequest;
import org.zmy.observabilityplatform.incident.interfaces.rest.response.IncidentNotificationResponse;
import org.zmy.observabilityplatform.incident.interfaces.rest.response.IncidentResponse;
import org.zmy.observabilityplatform.logging.interfaces.rest.response.TraceResponse;
import org.zmy.observabilityplatform.shared.interfaces.rest.PageResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {
    private final IncidentQueryService service;
    private final IncidentTraceQueryService traceQueryService;
    private final IncidentManagementService managementService;
    private final IncidentNotificationService notificationService;

    public IncidentController(IncidentQueryService service, IncidentTraceQueryService traceQueryService,
                              IncidentManagementService managementService,
                              IncidentNotificationService notificationService) {
        this.service = service;
        this.traceQueryService = traceQueryService;
        this.managementService = managementService;
        this.notificationService = notificationService;
    }

    /**
     * 查询最近产生的事件列表。
     */
    @GetMapping
    public Mono<PageResponse<IncidentResponse>> search(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentType type,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) Instant startedFrom,
            @RequestParam(required = false) Instant startedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        IncidentSearchQuery query = new IncidentSearchQuery(status, type, service, environment, assignee,
                startedFrom, startedTo, page, size);
        return this.service.search(query).map(result -> PageResponse.from(result, IncidentResponse::from));
    }

    /**
     * 根据事件 ID 查询事件详情。
     */
    @GetMapping("/{incidentId}")
    public Mono<IncidentResponse> findById(@PathVariable String incidentId) {
        return service.findById(incidentId).map(IncidentResponse::from);
    }

    @GetMapping("/{incidentId}/traces")
    public Flux<TraceResponse> findRelatedTraces(@PathVariable String incidentId,
                                                  @RequestParam(defaultValue = "10") int traceLimit,
                                                  @RequestParam(defaultValue = "1000") int logLimit) {
        return traceQueryService.findRelated(incidentId, traceLimit, logLimit).map(TraceResponse::from);
    }

    @PatchMapping("/{incidentId}/assignee")
    public Mono<IncidentResponse> assign(@PathVariable String incidentId,
                                         @Valid @RequestBody AssignIncidentRequest request) {
        return managementService.assign(incidentId, request.getAssignee()).map(IncidentResponse::from);
    }

    @PatchMapping("/{incidentId}/status")
    public Mono<IncidentResponse> transition(@PathVariable String incidentId,
                                             @Valid @RequestBody TransitionIncidentRequest request) {
        return managementService.transition(incidentId, request.getStatus(), request.getResolution())
                .map(IncidentResponse::from);
    }

    @GetMapping("/{incidentId}/notifications")
    public Flux<IncidentNotificationResponse> findNotifications(@PathVariable String incidentId,
                                                                 @RequestParam(defaultValue = "50") int limit) {
        return notificationService.findByIncidentId(incidentId, limit).map(IncidentNotificationResponse::from);
    }
}
