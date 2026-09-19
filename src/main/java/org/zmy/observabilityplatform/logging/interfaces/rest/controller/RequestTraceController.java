package org.zmy.observabilityplatform.logging.interfaces.rest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.logging.application.service.TraceQueryService;
import org.zmy.observabilityplatform.logging.interfaces.rest.response.TraceResponse;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/requests")
public class RequestTraceController {
    private final TraceQueryService service;

    public RequestTraceController(TraceQueryService service) {
        this.service = service;
    }

    @GetMapping("/{requestId}/traces")
    public Flux<TraceResponse> findTraces(@PathVariable String requestId,
                                          @RequestParam(defaultValue = "10") int traceLimit,
                                          @RequestParam(defaultValue = "1000") int logLimit) {
        return service.findByRequestId(requestId, traceLimit, logLimit).map(TraceResponse::from);
    }
}
