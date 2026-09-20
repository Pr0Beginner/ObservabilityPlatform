package org.zmy.observabilityplatform.shared.security.interfaces;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.zmy.observabilityplatform.audit.application.service.AuditOperation;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SecurityAuditOperationResolver {
    private static final Pattern INCIDENT_ASSIGN = Pattern.compile("^/api/v1/incidents/([^/]+)/assignee$");
    private static final Pattern INCIDENT_STATUS = Pattern.compile("^/api/v1/incidents/([^/]+)/status$");
    private static final Pattern DIAGNOSIS_CREATE = Pattern.compile("^/api/v1/incidents/([^/]+)/diagnoses$");
    private static final Pattern DIAGNOSIS_CANCEL = Pattern.compile("^/api/v1/diagnoses/([^/]+)/cancel$");
    private static final Pattern DIAGNOSIS_RETRY = Pattern.compile("^/api/v1/diagnoses/([^/]+)/retry$");
    private static final Pattern POLICY_UPDATE = Pattern.compile("^/api/v1/anomaly-policies/([^/]+)$");
    private static final Pattern DEAD_LETTER_REPLAY = Pattern.compile("^/api/v1/dead-letters/([^/]+)/replay$");

    public AuditOperation resolve(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        HttpMethod method = exchange.getRequest().getMethod();
        AuditOperation operation;
        if ((operation = match(path, INCIDENT_ASSIGN, AuditAction.INCIDENT_ASSIGN,
                AuditTargetType.INCIDENT)) != null) {
            return operation;
        }
        if ((operation = match(path, INCIDENT_STATUS, AuditAction.INCIDENT_TRANSITION,
                AuditTargetType.INCIDENT)) != null) {
            return operation;
        }
        if ((operation = match(path, DIAGNOSIS_CREATE, AuditAction.DIAGNOSIS_CREATE,
                AuditTargetType.INCIDENT)) != null) {
            return operation;
        }
        if ((operation = match(path, DIAGNOSIS_CANCEL, AuditAction.DIAGNOSIS_CANCEL,
                AuditTargetType.DIAGNOSIS)) != null) {
            return operation;
        }
        if ((operation = match(path, DIAGNOSIS_RETRY, AuditAction.DIAGNOSIS_RETRY,
                AuditTargetType.DIAGNOSIS)) != null) {
            return operation;
        }
        if (HttpMethod.POST.equals(method) && "/api/v1/anomaly-policies".equals(path)) {
            return new AuditOperation(AuditAction.ANOMALY_POLICY_CREATE,
                    AuditTargetType.ANOMALY_POLICY, "new");
        }
        if ((operation = match(path, POLICY_UPDATE, AuditAction.ANOMALY_POLICY_UPDATE,
                AuditTargetType.ANOMALY_POLICY)) != null) {
            return operation;
        }
        if ((operation = match(path, DEAD_LETTER_REPLAY, AuditAction.DEAD_LETTER_REPLAY,
                AuditTargetType.DEAD_LETTER)) != null) {
            return operation;
        }
        if (HttpMethod.POST.equals(method) && "/api/v1/logs/batch".equals(path)) {
            return new AuditOperation(AuditAction.LOG_INGEST, AuditTargetType.LOG_BATCH, path);
        }
        return new AuditOperation(AuditAction.ACCESS_REQUEST, AuditTargetType.HTTP_REQUEST, path);
    }

    private AuditOperation match(String path, Pattern pattern, AuditAction action,
                                 AuditTargetType targetType) {
        Matcher matcher = pattern.matcher(path);
        return matcher.matches() ? new AuditOperation(action, targetType, matcher.group(1)) : null;
    }
}
