package org.zmy.observabilityplatform.diagnosis.interfaces;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.diagnosis.application.DiagnosisService;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisTask;
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
    public Mono<DiagnosisTask> create(@PathVariable String incidentId) {
        return service.create(incidentId);
    }

    @GetMapping("/diagnoses/{taskId}")
    public Mono<DiagnosisService.DiagnosisView> findById(@PathVariable String taskId) {
        return service.findById(taskId);
    }
}
