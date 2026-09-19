package org.zmy.observabilityplatform.incident.infrastructure.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.incident.application.service.AnomalyEvaluationService;
import org.zmy.observabilityplatform.incident.domain.service.MetricWindowPolicy;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
public class AnomalyEvaluationScheduler {
    private final AnomalyEvaluationService service;
    private final MetricWindowPolicy requestWindowPolicy;
    private final Clock clock;

    public AnomalyEvaluationScheduler(AnomalyEvaluationService service, Clock clock,
                                      @Value("${app.incident.anomaly.window-minutes:5}") int windowMinutes) {
        this.service = service;
        this.clock = clock;
        this.requestWindowPolicy = new MetricWindowPolicy(windowMinutes);
    }

    @Scheduled(cron = "${app.incident.anomaly.evaluation-cron:0 * * * * *}")
    public void evaluateCompletedWindows() {
        Instant now = clock.instant();
        service.evaluate(requestWindowPolicy.previousCompletedWindow(now))
                .then(service.evaluateRepeatedErrors(now.truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.MINUTES)))
                .subscribe(null, error -> log.error("Failed to evaluate anomaly windows", error));
    }
}
