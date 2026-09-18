package org.zmy.observabilityplatform.diagnosis.application.dto;

import lombok.Value;

@Value
public class DiagnosisView {
    DiagnosisTaskView task;
    DiagnosisReportView report;
}
