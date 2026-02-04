package database

import (
	"backend-go/internal/common/result"
	"backend-go/internal/common/util"
	"context"
	"errors"
	"fmt"

	"gorm.io/gorm"
)

// BaseRepository 基础仓库
type BaseRepository[T any] struct {
	db *gorm.DB
}

func NewBaseRepository[T any](db *gorm.DB) *BaseRepository[T] {
	return &BaseRepository[T]{db: db}
}

// txKey 用于在context中存储事务实例的key
type txKey struct{}

// GetDB 从context中获取数据库实例，优先使用事务实例
func (r *BaseRepository[T]) GetDB(ctx context.Context) *gorm.DB {
	if tx, ok := ctx.Value(txKey{}).(*gorm.DB); ok {
		return tx
	}
	return r.db.WithContext(ctx)
}

// Create 创建, 成功后会修改传入的实体
func (r *BaseRepository[T]) Create(ctx context.Context, data *T) error {
	res := r.GetDB(ctx).Create(data)
	if res.Error != nil || res.RowsAffected == 0 {
		return res.Error
	}
	return nil
}

// FindByID 根据 ID 查询, 未查到时返回 nil
func (r *BaseRepository[T]) FindByID(ctx context.Context, id any) (*T, error) {
	var t T
	res := r.GetDB(ctx).Where("id = ?", id).Take(&t)
	if res.Error != nil {
		if errors.Is(res.Error, gorm.ErrRecordNotFound) {
		}
		return nil, nil
	}
	return &t, nil
}

// FindAll 根据条件查询
func (r *BaseRepository[T]) FindAll(ctx context.Context, query any, args ...any) (*[]T, error) {
	var ts []T
	res := r.GetDB(ctx).Where(query, args...).Find(&ts)
	if res.Error != nil {
		return nil, res.Error
	}
	return &ts, nil
}

// BuildQueryWithParam 拼接分页排序条件
func (r *BaseRepository[T]) BuildQueryWithParam(db *gorm.DB, param *result.QueryParam) (*gorm.DB, error) {
	if err := param.Valid(); err != nil {
		return nil, err
	}
	if param.HasPage() {
		offset := param.GetPageIndex() * param.GetPageSize()
		db = db.Offset(offset).Limit(param.GetPageSize())
	}
	if param.HasSort() {
		for _, sort := range param.Sorts {
			db = db.Order(fmt.Sprintf("%s %s", util.CamelToSnake(sort.Field), sort.Direction))
		}
	}
	return db, nil
}

// FindAllWithPage 根据条件分页查询
func (r *BaseRepository[T]) FindAllWithPage(ctx context.Context, pageParam *result.QueryParam, query any, args ...any) (*result.PageResult[T], error) {
	var t T
	q := r.GetDB(ctx).Model(&t).Where(query, args...)
	var total int64
	res := q.Count(&total)
	if res.Error != nil {
		return nil, res.Error
	}
	if total == 0 {
		return result.NewPageResult(&[]T{}, 0, 0, 0), nil
	}

	q, err := r.BuildQueryWithParam(q, pageParam)
	if err != nil {
		return nil, err
	}
	var ts []T
	res = q.Find(&ts)
	if res.Error != nil {
		return nil, res.Error
	}
	return result.NewPageResult(&ts, int(total), pageParam.GetPageIndex(), pageParam.GetPageSize()), nil
}

// Updates 更新, 成功后会修改传入的实体
func (r *BaseRepository[T]) Updates(ctx context.Context, data *T) error {
	return r.GetDB(ctx).Model(data).Updates(data).Error
}

// DeleteByID 根据 ID 删除
func (r *BaseRepository[T]) DeleteByID(ctx context.Context, id any) error {
	var t T
	res := r.GetDB(ctx).Where("id = ?", id).Delete(&t)
	return res.Error
}

// Transaction 开启事务，通过context传递事务实例
func (r *BaseRepository[T]) Transaction(base context.Context, fn func(ctx context.Context) error) error {
	return r.GetDB(base).Transaction(func(tx *gorm.DB) error {
		ctx := context.WithValue(tx.Statement.Context, txKey{}, tx)
		return fn(ctx)
	})
}
