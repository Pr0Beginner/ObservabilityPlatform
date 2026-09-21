package org.zmy.observabilityplatform.logging.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.logging.application.dto.LogSearchPage;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogPageResponse {
    private List<LogResponse> items;
    private String nextCursor;
    private boolean hasMore;

    public static LogPageResponse from(LogSearchPage page) {
        return new LogPageResponse(page.getItems().stream().map(LogResponse::from).toList(),
                page.getNextCursor(), page.isHasMore());
    }
}
