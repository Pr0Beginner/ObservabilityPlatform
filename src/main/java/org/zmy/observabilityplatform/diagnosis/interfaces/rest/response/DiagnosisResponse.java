package org.zmy.observabilityplatform.diagnosis.interfaces.rest.response;

import org.zmy.observabilityplatform.diagnosis.application.dto.DiagnosisView;

public record DiagnosisResponse(DiagnosisTaskResponse task, DiagnosisReportResponse report) {
    public static DiagnosisResponse from(DiagnosisView view) {
        return new DiagnosisResponse(DiagnosisTaskResponse.from(view.task()),
                view.report() == null ? null : DiagnosisReportResponse.from(view.report()));
    }
}
