package com.eastwind.EACAfterSaleMgr.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 基础查询参数
 */
@Data
public class QueryParam {
    /**
     * 页面大小
     */
    private Integer pageSize;

    /**
     * 页面索引，从 0 开始
     */
    private Integer pageIndex;

    /**
     * 排序参数
     */
    private List<SortParam> sorts;

    /**
     * 排序参数
     */
    @Data
    public static class SortParam {
        /**
         * 排序字段
         */
        private String field;

        /**
         * 排序方向
         */
        private String direction;
    }
}
