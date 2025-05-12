package com.eastwind.EACAfterSaleMgr.model.dto;

import java.util.List;

/**
 * 分页结果
 * @param items 数据
 * @param totalCount 总数
 * @param totalPages 总页数
 * @param pageSize 页面大小
 * @param pageIndex 页面索引
 * @param <T> 数据类型
 */
public record PageResult<T>(
        List<T> items,
        Long totalCount,
        Integer totalPages,
        Integer pageSize,
        Integer pageIndex
) {
}
