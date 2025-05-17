package pers.eastwind.billmanager.model.dto;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 批量操作结果
 */
@Data
@Slf4j
public class ActionsResult<P, R> {
    @Data
    @Builder
    public static class Row<P, R> {
        /**
         * 参数，用于前端判断是哪一条
         */
        private P param;
        /**
         * 结果
         */
        private R result;
        /**
         * 是否成功
         */
        private Boolean success;
        /**
         * 信息
         */
        private String message;

    }

    /**
     * 结果集
     */
    private List<Row<P, R>> results;
    /**
     * 成功条数
     */
    private Integer successCount;
    /**
     * 失败条数
     */
    private Integer failCount;


    /**
     * 执行批量操作
     * @param params 参数列表
     * @param action 动作
     * @return 批量操作结果
     * @param <P> 参数类型
     * @param <R> 结果类型
     */
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
                log.error("执行操作失败: {} {}", param.toString(), e.getMessage());
                rows.add(Row.<P, R>builder().param(param).success(false).message(e.getMessage()).build());
                result.setFailCount(result.getFailCount() + 1);
            }
        }
        return result;
    }
}
