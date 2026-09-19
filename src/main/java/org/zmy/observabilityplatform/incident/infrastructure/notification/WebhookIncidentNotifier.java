package org.zmy.observabilityplatform.incident.infrastructure.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.zmy.observabilityplatform.incident.application.notification.IncidentNotifier;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.NotificationType;
import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;

@Slf4j
@Component
public class WebhookIncidentNotifier implements IncidentNotifier {
    private final WebClient webClient;
    private final String webhookUrl;

    public WebhookIncidentNotifier(WebClient.Builder builder,
                                   @Value("${app.notification.webhook-url:}") String webhookUrl) {
        this.webClient = builder.build();
        this.webhookUrl = webhookUrl;
    }

    @Override
    public Mono<Void> send(Incident incident, IncidentNotification notification) {
        NotificationType type = notification.getType();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return Mono.fromRunnable(() -> log.info("Incident notification: type={}, incidentId={}, title={}",
                    type, incident.getId(), incident.getTitle()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type.name());
        payload.put("incidentId", incident.getId());
        payload.put("incidentType", incident.getType().name());
        payload.put("title", incident.getTitle());
        payload.put("service", incident.getService());
        payload.put("environment", incident.getEnvironment());
        payload.put("status", incident.getStatus().name());
        payload.put("occurredAt", incident.getUpdatedAt().toString());
        payload.put("durationMs", Math.max(0,
                Duration.between(incident.getStartedAt(), incident.getUpdatedAt()).toMillis()));
        if (incident.getOperation() != null) {
            payload.put("operation", incident.getOperation());
        }
        if (incident.getDimension() != null) {
            payload.put("dimension", incident.getDimension());
        }
        if (incident.getCurrentValue() != null) {
            payload.put("currentValue", incident.getCurrentValue());
        }
        if (incident.getBaselineValue() != null) {
            payload.put("baselineValue", incident.getBaselineValue());
        }
        return webClient.post().uri(webhookUrl)
                .header("Idempotency-Key", notification.getNotificationKey())
                .bodyValue(payload).retrieve().toBodilessEntity().then();
    }
}
