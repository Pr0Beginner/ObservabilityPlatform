package org.zmy.observabilityplatform.diagnosis.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisCompletedEvent;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosisDomainTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-09-18T08:30:00Z");

    @Test
    void completesOnlyWithAReportForTheSameTaskVersion() {
        DiagnosisTask task = DiagnosisTask.request("task-1", "incident-1", 1, REQUESTED_AT);
        DiagnosisReport wrongReport = DiagnosisReport.generate("report-1", "other-task", 1,
                "Database unavailable", 0.9, List.of("timeout"), List.of("check database"), List.of(),
                REQUESTED_AT.plusSeconds(10));

        assertThatThrownBy(() -> task.complete(wrongReport, REQUESTED_AT.plusSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");

        DiagnosisReport report = DiagnosisReport.generate("report-2", task.getId(), task.getVersion(),
                "Database unavailable", 0.9, List.of("timeout"), List.of("check database"), List.of(),
                REQUESTED_AT.plusSeconds(10));
        DiagnosisTask completed = task.complete(report, REQUESTED_AT.plusSeconds(10));

        assertThat(completed.getStatus()).isEqualTo(DiagnosisTaskStatus.SUCCEEDED);
        assertThatThrownBy(() -> completed.fail("late failure", REQUESTED_AT.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresFailureReasonAndValidConfidence() {
        DiagnosisTask task = DiagnosisTask.request("task-1", "incident-1", 1, REQUESTED_AT);

        assertThatThrownBy(() -> task.fail(" ", REQUESTED_AT.plusSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiagnosisReport.generate("report-1", "task-1", 1,
                "Database unavailable", 1.1, List.of(), List.of(), List.of(), REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void serializesAndRestoresCompletedEventPayload() throws Exception {
        DiagnosisCompletedEvent event = new DiagnosisCompletedEvent("event-1", "task-1", "incident-1", 1,
                "Database unavailable", 0.9, List.of("timeout"), List.of("check database"), List.of(),
                REQUESTED_AT, null);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

        DiagnosisCompletedEvent restored = objectMapper.readValue(
                objectMapper.writeValueAsString(event), DiagnosisCompletedEvent.class);

        assertThat(restored).isEqualTo(event);
    }
}
