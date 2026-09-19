package org.zmy.observabilityplatform.logging.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogItemRequest {
    private Instant timestamp;

    @NotBlank
    private String content;

    @NotBlank
    private String format;

    @Size(max = 64)
    private String traceId;

    @Size(max = 32)
    private String spanId;
    @Size(max = 32)
    private String parentSpanId;
    @Size(max = 255)
    private String requestId;
    @Size(max = 255)
    private String operation;
    @Size(max = 20)
    private String spanKind;
    private Integer statusCode;
    private Boolean success;
    @Size(max = 255)
    private String errorCode;
    private Long durationMs;
    private Map<String, Object> attributes;
}
