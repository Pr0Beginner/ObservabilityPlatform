package org.zmy.observabilityplatform.logging.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
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

    private String traceId;
    private Map<String, Object> attributes;
}
