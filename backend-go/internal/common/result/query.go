package result

import (
	"errors"
	"strings"
)

// SortParam 排序参数
type SortParam struct {
	Field     string `json:"field"`
	Direction string `json:"direction"`
}

// QueryParam 查询参数
type QueryParam struct {
	PageIndex *int         `json:"pageIndex"`
	PageSize  *int         `json:"pageSize"`
	Sorts     *[]SortParam `json:"sorts"`
}

// GetOffset 获取偏移量
func (p *QueryParam) GetOffset() int {
	if p.PageIndex == nil || p.PageSize == nil {
		return 0
	}
	return (*p.PageIndex - 1) * *p.PageSize
}

// Valid 校验并设置部分默认值
func (p *QueryParam) Valid() error {
	if p.HasPage() {
		if *p.PageSize < 1 || *p.PageIndex < 0 {
			return errors.New("分页参数不合法，索引必读非负，页大小必须大于0")
		}
	}
	if p.HasPage() {
		for _, sort := range *p.Sorts {
			if sort.Field == "" {
				return errors.New("排序字段不能为空")
			}
			if sort.Direction == "" {
				sort.Direction = "asc"
			}
			sort.Direction = strings.ToLower(sort.Direction)
			if sort.Direction != "asc" && sort.Direction != "desc" {
				return errors.New("排序方向只能是asc或desc")
			}
		}
	}
	return nil
}

// HasPage 是否有分页参数
func (p *QueryParam) HasPage() bool {
	return p.PageIndex != nil && p.PageSize != nil
}

// GetPageIndex 获取分页索引
func (p *QueryParam) GetPageIndex() int {
	if p.PageIndex == nil {
		return 0
	}
	return *p.PageIndex
}

// GetPageSize 获取分页大小
func (p *QueryParam) GetPageSize() int {
	if p.PageSize == nil {
		return -1
	}
	return *p.PageSize
}

// HasSort 是否有排序参数
func (p *QueryParam) HasSort() bool {
	return p.Sorts != nil && len(*p.Sorts) > 0
}

// PageResult 分页结果
type PageResult[T any] struct {
	Content    *[]T `json:"content"`
	Total      int  `json:"total"`
	PageIndex  int  `json:"pageIndex"`
	PageSize   int  `json:"pageSize"`
	TotalPages int  `json:"totalPages"`
}

// NewPageResult 创建分页结果
func NewPageResult[T any](content *[]T, total, pageIndex, pageSize int) *PageResult[T] {
	totalPages := total / pageSize
	if total%pageSize != 0 {
		totalPages++
	}
	return &PageResult[T]{
		Content:    content,
		Total:      total,
		PageIndex:  pageIndex,
		PageSize:   pageSize,
		TotalPages: totalPages,
	}
}

// NewPageResultFromDB 从数据库查询结果创建分页结果
func NewPageResultFromDB[E any, DTO any](result *PageResult[E], fn func(*[]E) *[]DTO) *PageResult[DTO] {
	return &PageResult[DTO]{
		Content:    fn(result.Content),
		Total:      result.Total,
		PageIndex:  result.PageIndex,
		PageSize:   result.PageSize,
		TotalPages: result.TotalPages,
	}
}
