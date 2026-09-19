package org.zmy.observabilityplatform.logging.domain.service;

import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import org.zmy.observabilityplatform.logging.domain.model.TraceCallTree;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceReconstructionServiceTest {
    private final TraceReconstructionService service = new TraceReconstructionService();

    @Test
    void reconstructsServiceCallTreeAndKeepsLogsWithoutSpan() {
        Instant start = Instant.parse("2026-09-19T10:00:00Z");
        LogEntry gateway = log("1", start, "gateway", "trace-1", "span-a", null,
                "POST /orders", true, 200, null, 300L, "INFO");
        LogEntry order = log("2", start.plusMillis(20), "order-service", "trace-1", "span-b", "span-a",
                "createOrder", true, 200, null, 250L, "INFO");
        LogEntry payment = log("3", start.plusMillis(50), "payment-service", "trace-1", "span-c", "span-b",
                "POST /payments", false, 504, "PAYMENT_TIMEOUT", 180L, "ERROR");
        LogEntry loose = log("4", start.plusMillis(10), "gateway", "trace-1", null, null,
                null, null, null, null, null, "INFO");

        TraceCallTree tree = service.reconstruct("trace-1", List.of(payment, loose, order, gateway), false);

        assertThat(tree.roots()).extracting("spanId").containsExactly("span-a");
        assertThat(tree.childrenOf("span-a")).extracting("spanId").containsExactly("span-b");
        assertThat(tree.childrenOf("span-b")).extracting("spanId").containsExactly("span-c");
        assertThat(tree.getUnassignedLogs()).containsExactly(loose);
        assertThat(tree.services()).containsExactly("gateway", "order-service", "payment-service");
        assertThat(tree.isSuccessful()).isFalse();
        assertThat(tree.durationMs()).isEqualTo(300);
    }

    @Test
    void detachesMissingParentsInsteadOfDroppingTheSpan() {
        Instant timestamp = Instant.parse("2026-09-19T10:00:00Z");
        LogEntry orphan = log("1", timestamp, "orders", "trace-1", "span-b", "missing",
                "createOrder", true, 200, null, 10L, "INFO");

        TraceCallTree tree = service.reconstruct("trace-1", List.of(orphan), false);

        assertThat(tree.roots()).hasSize(1);
        assertThat(tree.roots().get(0).getParentSpanId()).isNull();
    }

    private LogEntry log(String id, Instant timestamp, String service, String traceId, String spanId,
                         String parentSpanId, String operation, Boolean success, Integer statusCode,
                         String errorCode, Long durationMs, String level) {
        return LogEntry.create(id, "batch-1", timestamp, timestamp, service, "test", level, traceId,
                spanId, parentSpanId, "request-1", operation, "SERVER", statusCode, success, errorCode,
                durationMs, "message", "message", "fingerprint", Map.of());
    }
}
