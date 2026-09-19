package org.zmy.observabilityplatform.incident.infrastructure.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.incident.application.service.IncidentNotificationService;

@Slf4j
@Component
public class IncidentNotificationRetryScheduler {
    private final IncidentNotificationService service;

    public IncidentNotificationRetryScheduler(IncidentNotificationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.notification.retry-delay-ms:60000}")
    public void retryFailedNotifications() {
        service.retry(50).subscribe(null, error -> log.error("Failed to retry incident notifications", error));
    }
}
