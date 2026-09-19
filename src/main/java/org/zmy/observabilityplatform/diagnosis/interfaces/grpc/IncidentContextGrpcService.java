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
import org.zmy.observabilityplatform.incident.application.service.IncidentTraceQueryService;
import org.zmy.observabilityplatform.logging.application.dto.LogView;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@Component
public class IncidentContextGrpcService extends IncidentContextServiceGrpc.IncidentContextServiceImplBase {
    private final IncidentQueryService incidentQueryService;
    private final IncidentTraceQueryService incidentTraceQueryService;

    public IncidentContextGrpcService(IncidentQueryService incidentQueryService,
                                      IncidentTraceQueryService incidentTraceQueryService) {
        this.incidentQueryService = incidentQueryService;
        this.incidentTraceQueryService = incidentTraceQueryService;
    }

    @Override
    public void getIncidentContext(IncidentContextRequest request,
                                   StreamObserver<IncidentContextResponse> responseObserver) {
        // 限制证据日志数量，避免诊断上下文过大；未指定时使用默认值 50。
        int limit = Math.max(1, Math.min(request.getLogLimit() == 0 ? 50 : request.getLogLimit(), 200));
        Mono<IncidentContextResponse> response = incidentQueryService.findById(request.getIncidentId())
                .flatMap(incident -> incidentTraceQueryService.findRelatedLogs(incident.getId(), limit)
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
                .setFingerprint(value(incident.getFingerprint()))
                .setIncidentType(incident.getType())
                .setOperation(value(incident.getOperation()))
                .setDimension(value(incident.getDimension()));
        if (incident.getCurrentValue() != null) {
            builder.setCurrentValue(incident.getCurrentValue());
        }
        if (incident.getBaselineValue() != null) {
            builder.setBaselineValue(incident.getBaselineValue());
        }
        logs.stream().map(LogView::getTraceId).filter(Objects::nonNull).filter(value -> !value.isBlank())
                .distinct().forEach(builder::addRelatedTraceIds);
        logs.forEach(log -> builder.addLogs(toEvidence(log)));
        return builder.build();
    }

    private LogEvidence toEvidence(LogView log) {
        LogEvidence.Builder builder = LogEvidence.newBuilder()
                .setId(log.getId())
                .setTimestamp(log.getTimestamp().toString())
                .setLevel(log.getLevel())
                .setTraceId(value(log.getTraceId()))
                .setMessage(log.getMessage())
                .setFingerprint(log.getFingerprint())
                .setSpanId(value(log.getSpanId()))
                .setParentSpanId(value(log.getParentSpanId()))
                .setRequestId(value(log.getRequestId()))
                .setOperation(value(log.getOperation()))
                .setSpanKind(value(log.getSpanKind()))
                .setErrorCode(value(log.getErrorCode()));
        if (log.getStatusCode() != null) {
            builder.setStatusCode(log.getStatusCode());
        }
        if (log.getSuccess() != null) {
            builder.setSuccess(log.getSuccess());
        }
        if (log.getDurationMs() != null) {
            builder.setDurationMs(log.getDurationMs());
        }
        return builder.build();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
