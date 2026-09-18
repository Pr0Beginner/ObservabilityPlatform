package org.zmy.observabilityplatform.diagnosis.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.diagnosis.application.dto.DiagnosisView;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResponse {
    private DiagnosisTaskResponse task;
    private DiagnosisReportResponse report;

    public static DiagnosisResponse from(DiagnosisView view) {
        return new DiagnosisResponse(DiagnosisTaskResponse.from(view.getTask()),
                view.getReport() == null ? null : DiagnosisReportResponse.from(view.getReport()));
    }
}
