package org.zmy.observabilityplatform.shared.tracing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {
    @Test
    void continuesValidW3cContextWithANewSpan() {
        String traceId = "0af7651916cd43dd8448eb211c80319c";
        TraceContext context = TraceContext.continueFrom(
                "00-" + traceId + "-b7ad6b7169203331-01").orElseThrow();

        assertThat(context.getTraceId()).isEqualTo(traceId);
        assertThat(context.getParentSpanId()).isEqualTo("b7ad6b7169203331");
        assertThat(context.getSpanId()).hasSize(16).isNotEqualTo(context.getParentSpanId());
        assertThat(context.traceParent()).startsWith("00-" + traceId + "-");
    }

    @Test
    void rejectsInvalidContext() {
        assertThat(TraceContext.continueFrom("invalid")).isEmpty();
        assertThat(TraceContext.continueFrom(
                "00-00000000000000000000000000000000-b7ad6b7169203331-01")).isEmpty();
    }
}
