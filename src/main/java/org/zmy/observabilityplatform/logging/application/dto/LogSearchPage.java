package org.zmy.observabilityplatform.logging.application.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class LogSearchPage {
    private final List<LogView> items;
    private final String nextCursor;
    private final boolean hasMore;

    public LogSearchPage(List<LogView> items, String nextCursor, boolean hasMore) {
        this.items = List.copyOf(items);
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }
}
