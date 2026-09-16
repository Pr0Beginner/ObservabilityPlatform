package org.zmy.observabilityplatform.diagnosis.interfaces.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.contract.v1.IncidentContextRequest;
import org.zmy.observabilityplatform.contract.v1.IncidentContextResponse;
import org.zmy.observabilityplatform.contract.v1.IncidentContextServiceGrpc;
import org.zmy.observabilityplatform.contract.v1.LogEvidence;
import org.zmy.observabilityplatform.incident.domain.IncidentRepository;
import org.zmy.observabilityplatform.logging.domain.LogEntry;
import org.zmy.observabilityplatform.logging.domain.LogRepository;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class IncidentContextGrpcService extends IncidentContextServiceGrpc.IncidentContextServiceImplBase {
    private final IncidentRepository incidentRepository;
    private final LogRepository logRepository;

    public IncidentContextGrpcService(IncidentRepository incidentRepository, LogRepository logRepository) {
        this.incidentRepository = incidentRepository;
        this.logRepository = logRepository;
    }

    @Override
    public void getIncidentContext(IncidentContextRequest request,
                                   StreamObserver<IncidentContextResponse> responseObserver) {
        int limit = Math.max(1, Math.min(request.getLogLimit() == 0 ? 50 : request.getLogLimit(), 200));
        Mono<IncidentContextResponse> response = incidentRepository.findById(request.getIncidentId())
                .flatMap(incident -> logRepository.findByIncidentContext(incident.service(), incident.environment(),
                                incident.fingerprint(), limit)
                        .collectList()
                        .map(logs -> buildResponse(incident, logs)))
                .switchIfEmpty(Mono.error(Status.NOT_FOUND
                        .withDescription("Incident not found: " + request.getIncidentId()).asRuntimeException()));
        response.subscribe(value -> {
            responseObserver.onNext(value);
            responseObserver.onCompleted();
        }, error -> responseObserver.onError(Status.fromThrowable(error).asRuntimeException()));
    }

    private IncidentContextResponse buildResponse(org.zmy.observabilityplatform.incident.domain.Incident incident,
                                                   List<LogEntry> logs) {
        IncidentContextResponse.Builder builder = IncidentContextResponse.newBuilder()
                .setIncidentId(incident.id())
                .setTitle(incident.title())
                .setService(incident.service())
                .setEnvironment(incident.environment())
                .setSeverity(incident.severity().name())
                .setStatus(incident.status().name())
                .setFingerprint(incident.fingerprint());
        logs.forEach(log -> builder.addLogs(LogEvidence.newBuilder()
                .setId(log.id())
                .setTimestamp(log.timestamp().toString())
                .setLevel(log.level())
                .setTraceId(log.traceId() == null ? "" : log.traceId())
                .setMessage(log.message())
                .setFingerprint(log.fingerprint())
                .build()));
        return builder.build();
    }
}
