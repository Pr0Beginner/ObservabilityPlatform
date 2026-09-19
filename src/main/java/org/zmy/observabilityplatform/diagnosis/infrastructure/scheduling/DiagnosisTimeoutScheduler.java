package org.zmy.observabilityplatform.diagnosis.infrastructure.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.diagnosis.application.service.DiagnosisService;

import java.time.Duration;

@Slf4j
@Component
public class DiagnosisTimeoutScheduler {
    private final DiagnosisService service;
    private final Duration timeout;

    public DiagnosisTimeoutScheduler(DiagnosisService service,
                                     @Value("${app.diagnosis.timeout-minutes:10}") long timeoutMinutes) {
        this.service = service;
        this.timeout = Duration.ofMinutes(timeoutMinutes);
    }

    @Scheduled(fixedDelayString = "${app.diagnosis.timeout-scan-delay-ms:60000}")
    public void timeoutExpiredTasks() {
        service.timeoutExpired(timeout)
                .subscribe(null, error -> log.error("Failed to timeout diagnosis tasks", error));
    }
}
