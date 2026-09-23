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
    /** 当前页的日志数据。 */
    private List<LogResponse> items;

    /** 下一页游标；没有下一页时为空。 */
    private String nextCursor;

    /** 是否仍有满足条件的后续日志。 */
    private boolean hasMore;

    public static LogPageResponse from(LogSearchPage page) {
        return new LogPageResponse(page.getItems().stream().map(LogResponse::from).toList(),
                page.getNextCursor(), page.isHasMore());
    }
}
