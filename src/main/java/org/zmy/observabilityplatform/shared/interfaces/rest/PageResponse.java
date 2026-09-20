package org.zmy.observabilityplatform.shared.interfaces.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.shared.application.query.PageResult;

import java.util.List;
import java.util.function.Function;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        return new PageResponse<>(result.getItems().stream().map(mapper).toList(), result.getPage(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
