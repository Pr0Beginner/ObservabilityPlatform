package org.zmy.observabilityplatform.logging.interfaces.rest.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.logging.application.service.LogIngestionService;
import org.zmy.observabilityplatform.logging.application.service.LogQueryService;
import org.zmy.observabilityplatform.logging.interfaces.rest.assembler.LogRequestAssembler;
import org.zmy.observabilityplatform.logging.interfaces.rest.request.LogBatchRequest;
import org.zmy.observabilityplatform.logging.interfaces.rest.request.LogSearchRequest;
import org.zmy.observabilityplatform.logging.interfaces.rest.response.LogBatchResponse;
import org.zmy.observabilityplatform.logging.interfaces.rest.response.LogPageResponse;
import reactor.core.publisher.Mono;

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

    /**
     * 接收一批原始日志并提交到后续处理链路。
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<LogBatchResponse> ingest(@Valid @RequestBody LogBatchRequest request) {
        return ingestionService.ingest(requestAssembler.toCommand(request))
                .map(LogBatchResponse::from);
    }

    /**
     * 按组合条件查询日志，并返回可继续向后翻页的游标。
     */
    @GetMapping
    public Mono<LogPageResponse> search(@Valid @ModelAttribute LogSearchRequest request) {
        return queryService.search(requestAssembler.toQuery(request))
                .map(LogPageResponse::from);
    }
}
