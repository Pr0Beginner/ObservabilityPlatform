package org.zmy.observabilityplatform.logging.application.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class LogSearchPage {
    /** 当前页的日志数据。 */
    private final List<LogView> items;

    /** 下一页游标；没有下一页时为空。 */
    private final String nextCursor;

    /** 是否仍有满足条件的后续日志。 */
    private final boolean hasMore;

    public LogSearchPage(List<LogView> items, String nextCursor, boolean hasMore) {
        this.items = List.copyOf(items);
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }
}
