package org.zmy.observabilityplatform.incident.application.command;

import lombok.Value;

import java.util.List;

@Value
public class InspectLogBatchCommand {
    List<ObservedLogCommand> logs;
}
