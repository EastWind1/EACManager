package pers.eastwind.billmanager.common.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Data
@Slf4j
public class ActionsResult<P, R> {
    private List<Row<P, R>> results;
    private Integer successCount = 0;
    private Integer failCount = 0;

    public static <P, R> ActionsResult<P, R> executeActions(List<P> params, Function<P, R> action) {
        ActionsResult<P, R> result = new ActionsResult<>();
        List<ActionsResult.Row<P, R>> rows = new ArrayList<>();
        result.setResults(rows);
        for (P param : params) {
            try {
                R r = action.apply(param);
                rows.add(Row.<P, R>builder().param(param).success(true).result(r).build());
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                log.error("执行操作失败: {} {}", param.toString(), e.getMessage(), e);
                rows.add(Row.<P, R>builder().param(param).success(false).message(e.getMessage()).build());
                result.setFailCount(result.getFailCount() + 1);
            }
        }
        return result;
    }

    @Data
    @Builder
    public static class Row<P, R> {
        /**
         * 参数，用于前端判断是哪一条
         */
        private P param;
        private R result;
        private Boolean success;
        private String message;

    }
}
