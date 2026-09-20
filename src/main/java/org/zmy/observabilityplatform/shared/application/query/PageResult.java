package org.zmy.observabilityplatform.shared.application.query;

import lombok.Getter;

import java.util.List;

@Getter
public final class PageResult<T> {
    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    private PageResult(List<T> items, int page, int size, long totalElements) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must not be negative");
        }
        this.items = List.copyOf(items);
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalElements == 0 ? 0 : (int) ((totalElements - 1) / size + 1);
    }

    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        return new PageResult<>(items, page, size, totalElements);
    }
}
