package org.zmy.observabilityplatform.incident.interfaces.rest.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.zmy.observabilityplatform.incident.application.service.AnomalyPolicyManagementService;
import org.zmy.observabilityplatform.incident.interfaces.rest.request.CreateAnomalyPolicyRequest;
import org.zmy.observabilityplatform.incident.interfaces.rest.request.UpdateAnomalyPolicyRequest;
import org.zmy.observabilityplatform.incident.interfaces.rest.response.AnomalyPolicyResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/anomaly-policies")
public class AnomalyPolicyController {
    private final AnomalyPolicyManagementService service;

    public AnomalyPolicyController(AnomalyPolicyManagementService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<AnomalyPolicyResponse> findAll() {
        return service.findAll().map(AnomalyPolicyResponse::from);
    }

    @GetMapping("/{policyId}")
    public Mono<AnomalyPolicyResponse> findById(@PathVariable String policyId) {
        return service.findById(policyId).map(AnomalyPolicyResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AnomalyPolicyResponse> create(@Valid @RequestBody CreateAnomalyPolicyRequest request) {
        return service.create(request.toCommand()).map(AnomalyPolicyResponse::from);
    }

    @PutMapping("/{policyId}")
    public Mono<AnomalyPolicyResponse> update(@PathVariable String policyId,
                                              @Valid @RequestBody UpdateAnomalyPolicyRequest request) {
        return service.update(policyId, request.toCommand()).map(AnomalyPolicyResponse::from);
    }
}
