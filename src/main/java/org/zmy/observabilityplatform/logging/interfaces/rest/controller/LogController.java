package org.zmy.observabilityplatform.logging.interfaces.rest.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.logging.application.query.LogSearchQuery;
import org.zmy.observabilityplatform.logging.application.service.LogIngestionService;
import org.zmy.observabilityplatform.logging.application.service.LogQueryService;
import org.zmy.observabilityplatform.logging.interfaces.rest.assembler.LogRequestAssembler;
import org.zmy.observabilityplatform.logging.interfaces.rest.request.LogBatchRequest;
import org.zmy.observabilityplatform.logging.interfaces.rest.response.LogBatchResponse;
import org.zmy.observabilityplatform.logging.interfaces.rest.response.LogResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {
    private final LogIngestionService ingestionService;
    private final LogQueryService queryService;
    private final LogRequestAssembler requestAssembler;

    public LogController(LogIngestionService ingestionService,
                         LogQueryService queryService,
                         LogRequestAssembler requestAssembler) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
        this.requestAssembler = requestAssembler;
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<LogBatchResponse> ingest(@Valid @RequestBody LogBatchRequest request) {
        return ingestionService.ingest(requestAssembler.toCommand(request))
                .map(LogBatchResponse::from);
    }

    @GetMapping
    public Flux<LogResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fingerprint,
            @RequestParam(defaultValue = "100") int size) {
        return queryService.search(new LogSearchQuery(from, to, service, environment, level, traceId,
                        keyword, fingerprint, size))
                .map(LogResponse::from);
    }
}
