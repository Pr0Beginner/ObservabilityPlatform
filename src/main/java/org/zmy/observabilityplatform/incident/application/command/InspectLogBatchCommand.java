package org.zmy.observabilityplatform.incident.application.command;

import java.util.List;

public record InspectLogBatchCommand(List<ObservedLogCommand> logs) {
}
