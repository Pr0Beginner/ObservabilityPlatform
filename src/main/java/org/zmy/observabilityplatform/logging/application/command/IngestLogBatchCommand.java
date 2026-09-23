package org.zmy.observabilityplatform.logging.application.command;

import lombok.Value;

import java.util.List;

@Value
public class IngestLogBatchCommand {
    /** 日志批次唯一标识，用于幂等接收。 */
    String batchId;

    /** 产生日志的服务名称。 */
    String service;

    /** 日志所属环境。 */
    String environment;

    /** 本批次包含的日志记录。 */
    List<IngestLogItemCommand> logs;
}
