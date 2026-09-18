package org.zmy.observabilityplatform.diagnosis.interfaces.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.diagnosis.application.service.DiagnosisService;
import org.zmy.observabilityplatform.diagnosis.interfaces.rest.response.DiagnosisResponse;
import org.zmy.observabilityplatform.diagnosis.interfaces.rest.response.DiagnosisTaskResponse;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
public class DiagnosisController {
    private final DiagnosisService service;

    public DiagnosisController(DiagnosisService service) {
        this.service = service;
    }

    @PostMapping("/incidents/{incidentId}/diagnoses")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<DiagnosisTaskResponse> create(@PathVariable String incidentId) {
        return service.create(incidentId).map(DiagnosisTaskResponse::from);
    }

    @GetMapping("/diagnoses/{taskId}")
    public Mono<DiagnosisResponse> findById(@PathVariable String taskId) {
        return service.findById(taskId).map(DiagnosisResponse::from);
    }
}
