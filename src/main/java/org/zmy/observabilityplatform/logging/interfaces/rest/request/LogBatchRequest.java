package org.zmy.observabilityplatform.logging.interfaces.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogBatchRequest {
    @NotBlank
    private String batchId;

    @NotBlank
    private String service;

    @NotBlank
    private String environment;

    @NotEmpty
    @Size(max = 1000)
    private List<@Valid LogItemRequest> logs;
}
