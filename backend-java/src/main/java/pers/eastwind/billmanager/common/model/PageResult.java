package pers.eastwind.billmanager.common.model;

import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 分页结果，pageIndex 从 0 开始
 */
public record PageResult<T>(
        List<T> items,
        Long totalCount,
        Integer totalPages,
        Integer pageSize,
        Integer pageIndex
) {
    public static <T> PageResult<T> empty() {
        return new PageResult<>(List.of(), 0L, 0, 0, 0);
    }

    public static <T> PageResult<T> fromPage(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber()
        );
    }

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
