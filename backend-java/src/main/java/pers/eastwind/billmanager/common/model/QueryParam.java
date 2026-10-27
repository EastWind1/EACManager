package pers.eastwind.billmanager.common.model;

import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pers.eastwind.billmanager.common.exception.BizException;

import java.util.ArrayList;
import java.util.List;

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

    private List<SortParam> sorts;

    /**
     * 两个分页参数都为空时不分页，只给其中一个会报错
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

    @Data
    public static class SortParam {
        private String field;

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
}
