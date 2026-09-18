package org.zmy.observabilityplatform.logging.interfaces.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LogBatchRequest(
        @NotBlank String batchId,
        @NotBlank String service,
        @NotBlank String environment,
        @NotEmpty @Size(max = 1000) List<@Valid LogItemRequest> logs) {
}
