package org.zmy.observabilityplatform.incident.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.incident.application.dto.IncidentView;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentResponse {
    private String id;
    private String dedupKey;
    private String title;
    private String service;
    private String environment;
    private String fingerprint;
    private String severity;
    private String status;
    private Instant startedAt;
    private Instant updatedAt;
    private long errorCount;
    private String assignee;
    private String resolution;

    public static IncidentResponse from(IncidentView view) {
        return new IncidentResponse(view.getId(), view.getDedupKey(), view.getTitle(), view.getService(),
                view.getEnvironment(), view.getFingerprint(), view.getSeverity(), view.getStatus(),
                view.getStartedAt(), view.getUpdatedAt(), view.getErrorCount(), view.getAssignee(),
                view.getResolution());
    }
}
