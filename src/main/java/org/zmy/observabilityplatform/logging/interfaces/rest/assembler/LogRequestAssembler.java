package org.zmy.observabilityplatform.logging.interfaces.rest.assembler;

import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.logging.application.command.IngestLogBatchCommand;
import org.zmy.observabilityplatform.logging.application.command.IngestLogItemCommand;
import org.zmy.observabilityplatform.logging.application.query.LogCursorCodec;
import org.zmy.observabilityplatform.logging.application.query.LogSearchQuery;
import org.zmy.observabilityplatform.logging.interfaces.rest.request.LogBatchRequest;
import org.zmy.observabilityplatform.logging.interfaces.rest.request.LogSearchRequest;

@Component
public class LogRequestAssembler {
    private final LogCursorCodec cursorCodec;

    public LogRequestAssembler(LogCursorCodec cursorCodec) {
        this.cursorCodec = cursorCodec;
    }

    /**
     * 将外部日志批次请求转换为应用层写入命令，避免应用层依赖 HTTP DTO。
     */
    public IngestLogBatchCommand toCommand(LogBatchRequest request) {
        return new IngestLogBatchCommand(request.getBatchId(), request.getService(), request.getEnvironment(),
                request.getLogs().stream()
                        .map(item -> new IngestLogItemCommand(item.getTimestamp(), item.getContent(), item.getFormat(),
                                item.getTraceId(), item.getSpanId(), item.getParentSpanId(), item.getRequestId(),
                                item.getOperation(), item.getSpanKind(), item.getStatusCode(), item.getSuccess(),
                                item.getErrorCode(), item.getDurationMs(), item.getAttributes()))
                        .toList());
    }

    /**
     * 将 HTTP 查询参数转换为应用层查询条件，并在接口边界解析不透明游标。
     */
    public LogSearchQuery toQuery(LogSearchRequest request) {
        return new LogSearchQuery(request.getFrom(), request.getTo(), request.getService(), request.getEnvironment(),
                request.getLevel(), request.getTraceId(), request.getSpanId(), request.getRequestId(),
                request.getKeyword(), request.getFingerprint(), cursorCodec.decode(request.getCursor()),
                request.getSize());
    }
}
