package pers.eastwind.billmanager.common.model;

import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pers.eastwind.billmanager.common.exception.BizException;

import java.util.ArrayList;
import java.util.List;

/**
 * 基础查询参数
 */
@Data
public class QueryParam {
    /**
     * 页面大小，为空或至少为 1
     */
    private Integer pageSize;

    /**
     * 页面索引，为空或至少为 0
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

        public void setDirection(String direction) {
            if (direction == null) {
                direction = "asc";
            }
            direction = direction.toLowerCase();
            if (!"asc".equals(direction) && !"desc".equals(direction)) {
                throw new BizException("排序方向必须为 asc 或 desc");
            }
            this.direction = direction;
        }
        public void setField(String field) {
            if (field == null || field.isEmpty()) {
                throw new BizException("排序字段不能为空");
            }
            this.field = field;
        }
    }

    /**
     * 获取分页参数
     * @return 分页参数
     */
    public Pageable getPageable() {
        Sort sort;
        if (sorts == null || sorts.isEmpty()) {
            sort = Sort.unsorted();
        } else {
            List<Sort.Order> orders = new ArrayList<>();
            for (SortParam sortParam : sorts) {
                switch (sortParam.getDirection()) {
                    case "asc" -> orders.add(Sort.Order.asc(sortParam.getField()));
                    case "desc" -> orders.add(Sort.Order.desc(sortParam.getField()));
                }
            }
            sort = Sort.by(orders);
        }
        if (pageSize == null && pageIndex == null) {
            return Pageable.unpaged(sort);
        } else {
            if (pageSize == null || pageIndex == null) {
                throw new BizException("分页参数不能只有一个为空");
            }
            return PageRequest.of(pageIndex, pageSize, sort);
        }
    }
}
