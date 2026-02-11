package pers.eastwind.billmanager.common.model;

import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 分页结果
 *
 * @param items      数据
 * @param totalCount 总数
 * @param totalPages 总页数
 * @param pageSize   页面大小
 * @param pageIndex  页面索引, 从 0 开始
 * @param <T>        数据类型
 */
public record PageResult<T>(
        List<T> items,
        Long totalCount,
        Integer totalPages,
        Integer pageSize,
        Integer pageIndex
) {
    /**
     * 空分页结果
     * @return 空分页结果
     * @param <T> 数据类型
     */
    public static<T> PageResult<T> empty() {
        return new PageResult<T>(List.of(), 0L, 0, 0, 0);
    }

    /**
     * 从 Page 转换为 PageResult
     * @param page Page
     * @return PageResult
     * @param <T> 数据类型
     */
    public static <T> PageResult<T> fromPage(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber()
        );
    }

    /**
     * 从 Page 转换为 PageResult
     * @param page Page
     * @param mapper 转换函数
     * @return PageResult
     * @param <T> 数据类型
     * @param <DTO> DTO 类型
     */
    public static <T, DTO> PageResult<DTO> fromPage(Page<T> page, Function<T, DTO> mapper) {
        List<DTO> items = new ArrayList<>();
        for (T t : page.getContent()) {
            items.add(mapper.apply(t));
        }
        return new PageResult<>(
                items,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber()
        );
    }
}
