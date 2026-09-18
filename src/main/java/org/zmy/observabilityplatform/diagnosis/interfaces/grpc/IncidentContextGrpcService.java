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
        // 限制证据日志数量，避免诊断上下文过大；未指定时使用默认值 50。
        int limit = Math.max(1, Math.min(request.getLogLimit() == 0 ? 50 : request.getLogLimit(), 200));
        Mono<IncidentContextResponse> response = incidentQueryService.findById(request.getIncidentId())
                .flatMap(incident -> logQueryService.findIncidentContext(incident.getService(),
                                incident.getEnvironment(), incident.getFingerprint(), limit)
                        .collectList()
                        .map(logs -> buildResponse(incident, logs)))
                .onErrorMap(NotFoundException.class, error -> Status.NOT_FOUND
                        .withDescription(error.getMessage()).asRuntimeException());
        // 在 gRPC 的观察者模型边界订阅响应式链路，并统一转发完成或错误信号。
        response.subscribe(value -> {
            responseObserver.onNext(value);
            responseObserver.onCompleted();
        }, error -> responseObserver.onError(Status.fromThrowable(error).asRuntimeException()));
    }

    private IncidentContextResponse buildResponse(IncidentView incident, List<LogView> logs) {
        IncidentContextResponse.Builder builder = IncidentContextResponse.newBuilder()
                .setIncidentId(incident.getId())
                .setTitle(incident.getTitle())
                .setService(incident.getService())
                .setEnvironment(incident.getEnvironment())
                .setSeverity(incident.getSeverity())
                .setStatus(incident.getStatus())
                .setFingerprint(incident.getFingerprint());
        logs.forEach(log -> builder.addLogs(LogEvidence.newBuilder()
                .setId(log.getId())
                .setTimestamp(log.getTimestamp().toString())
                .setLevel(log.getLevel())
                .setTraceId(log.getTraceId() == null ? "" : log.getTraceId())
                .setMessage(log.getMessage())
                .setFingerprint(log.getFingerprint())
                .build()));
        return builder.build();
    }
}
