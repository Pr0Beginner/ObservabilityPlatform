package org.zmy.observabilityplatform.logging.application.command;

import lombok.Value;

import java.util.List;

@Value
public class IngestLogBatchCommand {
    String batchId;
    String service;
    String environment;
    List<IngestLogItemCommand> logs;
}
