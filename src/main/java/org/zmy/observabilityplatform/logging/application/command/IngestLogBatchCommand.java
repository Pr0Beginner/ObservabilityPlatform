package org.zmy.observabilityplatform.logging.application.command;

import java.util.List;

public record IngestLogBatchCommand(
        String batchId,
        String service,
        String environment,
        List<IngestLogItemCommand> logs) {
}
