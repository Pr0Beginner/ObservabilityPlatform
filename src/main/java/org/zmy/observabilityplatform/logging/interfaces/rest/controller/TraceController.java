package org.zmy.observabilityplatform.logging.interfaces.rest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.logging.application.service.TraceQueryService;
import org.zmy.observabilityplatform.logging.interfaces.rest.response.TraceResponse;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/traces")
public class TraceController {
    private final TraceQueryService service;

    public TraceController(TraceQueryService service) {
        this.service = service;
    }

    @GetMapping("/{traceId}")
    public Mono<TraceResponse> findById(@PathVariable String traceId,
                                        @RequestParam(defaultValue = "1000") int limit) {
        return service.findById(traceId, limit).map(TraceResponse::from);
    }
}
