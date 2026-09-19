package org.zmy.observabilityplatform.incident.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentNotificationResponse {
    private String id;
    private String type;
    private String status;
    private int attempts;
    private Instant createdAt;
    private Instant updatedAt;
    private String lastError;

    public static IncidentNotificationResponse from(IncidentNotification notification) {
        return new IncidentNotificationResponse(notification.getId(), notification.getType().name(),
                notification.getStatus().name(), notification.getAttempts(), notification.getCreatedAt(),
                notification.getUpdatedAt(), notification.getLastError());
    }
}
