package org.zmy.observabilityplatform.incident.application.notification;

import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;
import reactor.core.publisher.Mono;

public interface IncidentNotifier {
    Mono<Void> send(Incident incident, IncidentNotification notification);
}
