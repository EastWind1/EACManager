package result

import (
	"backend-go/pkg/errs"
	"strings"
)

type SortParam struct {
	Field     string `json:"field"`
	Direction string `json:"direction"`
}

type QueryParam struct {
	// PageIndex 从 0 开始，与 Java 侧 PageRequest 及前端 options.page-1 一致
	PageIndex *int        `json:"pageIndex"`
	PageSize  *int        `json:"pageSize"`
	Sorts     []SortParam `json:"sorts"`
}

func (p *QueryParam) GetOffset() int {
	if p.PageIndex == nil || p.PageSize == nil {
		return 0
	}
	return *p.PageIndex * *p.PageSize
}

// Valid 校验参数并把排序方向归一化为 asc/desc
//
// 不判断排序字段是否真实存在：列名由 clause.OrderByColumn 交给 GORM 加引号，非法列由数据库报错。
func (p *QueryParam) Valid() error {
	if p.HasPage() && (*p.PageSize < 1 || *p.PageIndex < 0) {
		return errs.NewBizError("分页参数不合法，索引必须非负，页大小必须大于0")
	}
	for i := range p.Sorts {
		if p.Sorts[i].Field == "" {
			return errs.NewBizError("排序字段不能为空")
		}
		direction := strings.ToLower(strings.TrimSpace(p.Sorts[i].Direction))
		if direction == "" {
			direction = "asc"
		}
		if direction != "asc" && direction != "desc" {
			return errs.NewBizError("排序方向只能是asc或desc")
		}
		p.Sorts[i].Direction = direction
	}
	return nil
}

func (p *QueryParam) HasPage() bool {
	return p.PageIndex != nil && p.PageSize != nil
}

func (p *QueryParam) GetPageIndex() int {
	if p.PageIndex == nil {
		return 0
	}
	return *p.PageIndex
}

// GetPageSize 未指定分页时返回 -1，供调用方区分「不分页」
func (p *QueryParam) GetPageSize() int {
	if p.PageSize == nil {
		return -1
	}
	return *p.PageSize
}

func (p *QueryParam) HasSort() bool {
	return len(p.Sorts) > 0
}

type PageResult[T any] struct {
	Items      []T `json:"items"`
	TotalCount int `json:"totalCount"`
	PageIndex  int `json:"pageIndex"`
	PageSize   int `json:"pageSize"`
	TotalPages int `json:"totalPages"`
}

func NewPageResult[T any](items []T, totalCount, pageIndex, pageSize int) *PageResult[T] {
	// 未指定分页时 GetPageSize 返回 -1，对外统一按「不分页」表示，避免负的 totalPages
	if pageSize <= 0 {
		return &PageResult[T]{Items: items, TotalCount: totalCount}
	}
	totalPages := totalCount / pageSize
	if totalCount%pageSize != 0 {
		totalPages++
	}
	return &PageResult[T]{
		Items:      items,
		TotalCount: totalCount,
		PageIndex:  pageIndex,
		PageSize:   pageSize,
		TotalPages: totalPages,
	}
}

func NewDTOPageResult[E any, DTO any](result *PageResult[E], fn func([]E) []DTO) *PageResult[DTO] {
	return &PageResult[DTO]{
		Items:      fn(result.Items),
		TotalCount: result.TotalCount,
		PageIndex:  result.PageIndex,
		PageSize:   result.PageSize,
		TotalPages: result.TotalPages,
	}
}
