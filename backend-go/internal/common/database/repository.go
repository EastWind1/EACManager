package database

import (
	"backend-go/internal/common/result"
	"context"
	"errors"
	"fmt"

	"gorm.io/gorm"
)

// BaseRepository 基础仓库
type BaseRepository[T any] struct {
	Db *gorm.DB
}

// Create 创建, 成功后会修改传入的实体
func (r *BaseRepository[T]) Create(ctx context.Context, data *T) error {
	res := gorm.WithResult()
	err := gorm.G[T](r.Db, res).Create(ctx, data)
	if err != nil || res.RowsAffected == 0 {
		return err
	}
	return nil
}

// FindByID 根据 ID 查询, 未查到时返回 nil
func (r *BaseRepository[T]) FindByID(ctx context.Context, id any) (*T, error) {
	t, err := gorm.G[T](r.Db).Where("id = ?", id).First(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &t, nil
}

// FindAll 根据条件查询
func (r *BaseRepository[T]) FindAll(ctx context.Context, query any, args ...any) (*[]T, error) {
	res, err := gorm.G[T](r.Db).Where(query, args...).Find(ctx)
	if err != nil {
		return nil, err
	}
	return &res, nil
}

// FindAllWithPage 根据条件分页查询
func (r *BaseRepository[T]) FindAllWithPage(ctx context.Context, pageParam *result.QueryParam, query any, args ...any) (*result.PageResult[T], error) {
	if err := pageParam.Valid(); err != nil {
		return nil, err
	}
	q := gorm.G[T](r.Db).Where(query, args...)
	total, err := q.Count(ctx, "id")
	if err != nil {
		return nil, err
	}
	if total == 0 {
		return result.NewPageResult(&[]T{}, 0, 0, 0), nil
	}
	if pageParam.HasPage() {
		offset := pageParam.GetPageIndex() * pageParam.GetPageSize()
		q = q.Offset(offset).Limit(pageParam.GetPageSize())
	}
	if pageParam.HasSort() {
		for _, sort := range *pageParam.Sorts {
			q = q.Order(fmt.Sprintf("%s %s", sort.Field, sort.Direction))
		}
	}
	res, err := q.Find(ctx)
	if err != nil {
		return nil, err
	}
	return result.NewPageResult(&res, int(total), pageParam.GetPageIndex(), pageParam.GetPageSize()), nil
}

// Save 保存, 成功后会修改传入的实体
func (r *BaseRepository[T]) Save(ctx context.Context, data *T) error {
	return r.Db.WithContext(ctx).Save(data).Error
}

// DeleteByID 根据 ID 删除
func (r *BaseRepository[T]) DeleteByID(ctx context.Context, id any) error {
	rowNum, err := gorm.G[T](r.Db).Where("id = ?", 10).Delete(ctx)
	if rowNum == 0 {
		return gorm.ErrRecordNotFound
	}
	return err
}

// Transaction 开启事务
func (r *BaseRepository[T]) Transaction(fn func(tx *gorm.DB) error) error {
	return r.Db.Transaction(fn)
}

func NewBaseRepository[T any](db *gorm.DB) *BaseRepository[T] {
	return &BaseRepository[T]{Db: db}
}
