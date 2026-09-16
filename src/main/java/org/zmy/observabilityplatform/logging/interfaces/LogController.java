package org.zmy.observabilityplatform.logging.interfaces;

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
import org.zmy.observabilityplatform.logging.application.LogIngestionService;
import org.zmy.observabilityplatform.logging.application.LogQueryService;
import org.zmy.observabilityplatform.logging.domain.LogEntry;
import org.zmy.observabilityplatform.logging.domain.LogSearchQuery;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {
    private final LogIngestionService ingestionService;
    private final LogQueryService queryService;

    public LogController(LogIngestionService ingestionService, LogQueryService queryService) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<LogIngestionService.IngestionResult> ingest(@Valid @RequestBody LogBatchRequest request) {
        return ingestionService.ingest(request.toDomain());
    }

    @GetMapping
    public Flux<LogEntry> search(
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
                keyword, fingerprint, size));
    }
}
