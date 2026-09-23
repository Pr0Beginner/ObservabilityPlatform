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
    /** 日志批次唯一标识，用于幂等接收。 */
    @NotBlank
    @Size(max = 255)
    private String batchId;

    /** 产生日志的服务名称。 */
    @NotBlank
    @Size(max = 120)
    private String service;

    /** 日志所属环境。 */
    @NotBlank
    @Size(max = 80)
    private String environment;

    /** 本批次包含的日志记录，最多 1000 条。 */
    @NotEmpty
    @Size(max = 1000)
    private List<@Valid LogItemRequest> logs;
}
