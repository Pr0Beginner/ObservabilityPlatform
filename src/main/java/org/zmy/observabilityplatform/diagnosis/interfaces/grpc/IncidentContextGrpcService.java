package org.zmy.observabilityplatform.diagnosis.interfaces.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.contract.v1.IncidentContextRequest;
import org.zmy.observabilityplatform.contract.v1.IncidentContextResponse;
import org.zmy.observabilityplatform.contract.v1.IncidentContextServiceGrpc;
import org.zmy.observabilityplatform.contract.v1.LogEvidence;
import org.zmy.observabilityplatform.incident.application.dto.IncidentView;
import org.zmy.observabilityplatform.incident.application.service.IncidentQueryService;
import org.zmy.observabilityplatform.logging.application.dto.LogView;
import org.zmy.observabilityplatform.logging.application.service.LogQueryService;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class IncidentContextGrpcService extends IncidentContextServiceGrpc.IncidentContextServiceImplBase {
    private final IncidentQueryService incidentQueryService;
    private final LogQueryService logQueryService;

    public IncidentContextGrpcService(IncidentQueryService incidentQueryService, LogQueryService logQueryService) {
        this.incidentQueryService = incidentQueryService;
        this.logQueryService = logQueryService;
    }

    @Override
    public void getIncidentContext(IncidentContextRequest request,
                                   StreamObserver<IncidentContextResponse> responseObserver) {
        int limit = Math.max(1, Math.min(request.getLogLimit() == 0 ? 50 : request.getLogLimit(), 200));
        Mono<IncidentContextResponse> response = incidentQueryService.findById(request.getIncidentId())
                .flatMap(incident -> logQueryService.findIncidentContext(incident.service(), incident.environment(),
                                incident.fingerprint(), limit)
                        .collectList()
                        .map(logs -> buildResponse(incident, logs)))
                .onErrorMap(NotFoundException.class, error -> Status.NOT_FOUND
                        .withDescription(error.getMessage()).asRuntimeException());
        response.subscribe(value -> {
            responseObserver.onNext(value);
            responseObserver.onCompleted();
        }, error -> responseObserver.onError(Status.fromThrowable(error).asRuntimeException()));
    }

    private IncidentContextResponse buildResponse(IncidentView incident, List<LogView> logs) {
        IncidentContextResponse.Builder builder = IncidentContextResponse.newBuilder()
                .setIncidentId(incident.id())
                .setTitle(incident.title())
                .setService(incident.service())
                .setEnvironment(incident.environment())
                .setSeverity(incident.severity())
                .setStatus(incident.status())
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
