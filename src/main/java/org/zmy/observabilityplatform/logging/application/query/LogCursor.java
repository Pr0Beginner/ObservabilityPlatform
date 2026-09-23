package org.zmy.observabilityplatform.logging.application.query;

import lombok.Value;

@Value
public class LogCursor {
    /** 当前页最后一条日志的毫秒时间戳。 */
    long timestampEpochMillis;

    /** 当前页最后一条日志的唯一标识，用于时间戳相同时稳定排序。 */
    String id;

    public LogCursor(long timestampEpochMillis, String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("cursor id must not be blank");
        }
        this.timestampEpochMillis = timestampEpochMillis;
        this.id = id;
    }
}
