package org.zmy.observabilityplatform.diagnosis.interfaces.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.zmy.observabilityplatform.diagnosis.application.service.DiagnosisService;
import org.zmy.observabilityplatform.diagnosis.interfaces.rest.response.DiagnosisResponse;
import org.zmy.observabilityplatform.diagnosis.interfaces.rest.response.DiagnosisTaskResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1")
public class DiagnosisController {
    private final DiagnosisService service;

    public DiagnosisController(DiagnosisService service) {
        this.service = service;
    }

    /**
     * 为指定事件创建异步诊断任务。
     */
    @PostMapping("/incidents/{incidentId}/diagnoses")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<DiagnosisTaskResponse> create(@PathVariable String incidentId) {
        return service.create(incidentId).map(DiagnosisTaskResponse::from);
    }

    @GetMapping("/incidents/{incidentId}/diagnoses")
    public Flux<DiagnosisTaskResponse> findByIncidentId(@PathVariable String incidentId,
                                                        @RequestParam(defaultValue = "50") int limit) {
        return service.findByIncidentId(incidentId, limit).map(DiagnosisTaskResponse::from);
    }

    /**
     * 查询诊断任务及其诊断报告。
     */
    @GetMapping("/diagnoses/{taskId}")
    public Mono<DiagnosisResponse> findById(@PathVariable String taskId) {
        return service.findById(taskId).map(DiagnosisResponse::from);
    }

    @PostMapping("/diagnoses/{taskId}/cancel")
    public Mono<DiagnosisTaskResponse> cancel(@PathVariable String taskId) {
        return service.cancel(taskId).map(DiagnosisTaskResponse::from);
    }

    @PostMapping("/diagnoses/{taskId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<DiagnosisTaskResponse> retry(@PathVariable String taskId) {
        return service.retry(taskId).map(DiagnosisTaskResponse::from);
    }
}
