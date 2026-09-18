package org.zmy.observabilityplatform.logging.interfaces.rest.assembler;

import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.logging.application.command.IngestLogBatchCommand;
import org.zmy.observabilityplatform.logging.application.command.IngestLogItemCommand;
import org.zmy.observabilityplatform.logging.interfaces.rest.request.LogBatchRequest;

@Component
public class LogRequestAssembler {
    public IngestLogBatchCommand toCommand(LogBatchRequest request) {
        return new IngestLogBatchCommand(request.batchId(), request.service(), request.environment(),
                request.logs().stream()
                        .map(item -> new IngestLogItemCommand(item.timestamp(), item.content(), item.format(),
                                item.traceId(), item.attributes()))
                        .toList());
    }
}
