package database

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"backend-go/pkg/util"
	"context"
	"errors"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// BaseRepository 基础仓库
type BaseRepository[T any] struct {
	db *gorm.DB
}

func NewBaseRepository[T any](db *gorm.DB) *BaseRepository[T] {
	return &BaseRepository[T]{db: db}
}

// TxKey 用于在 context 中携带事务实例
//
// 用于事务传播时，业务无须引入 Gorm 相关依赖
type TxKey struct{}

// RelationBatchSize 关联表批量写入的行数上限
const RelationBatchSize = 30

// Ctx 返回绑定事务（若 ctx 中携带）或请求 context 的会话
func (r *BaseRepository[T]) Ctx(ctx context.Context) *gorm.DB {
	if tx, ok := ctx.Value(TxKey{}).(*gorm.DB); ok {
		return tx.WithContext(ctx)
	}
	return r.db.WithContext(ctx)
}

// Chain 返回泛型条件链
//
// gorm.G[T] 返回的是 Interface[T]，它的 Omit 签名与 ChainInterface[T] 不兼容，不能直接当链用；
// 调一次链方法才会转成 ChainInterface，所以这里用 1 = 1 作为无操作的起点。
func (r *BaseRepository[T]) Chain(ctx context.Context) gorm.ChainInterface[T] {
	return gorm.G[T](r.Ctx(ctx)).Where("1 = 1")
}

// Create 创建, 成功后会修改传入的实体
func (r *BaseRepository[T]) Create(ctx context.Context, data *T) error {
	if err := gorm.G[T](r.Ctx(ctx)).Create(ctx, data); err != nil {
		return errs.Wrap(err)
	}
	return nil
}

// FindByID 未查到时返回 nil, nil，调用方据此区分「不存在」与查询错误。
func (r *BaseRepository[T]) FindByID(ctx context.Context, id any) (*T, error) {
	record, err := gorm.G[T](r.Ctx(ctx)).Where("id = ?", id).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &record, nil
}

func (r *BaseRepository[T]) FindAll(ctx context.Context, query any, args ...any) ([]T, error) {
	var records []T
	var err error
	if query != nil {
		records, err = gorm.G[T](r.Ctx(ctx)).Where(query, args...).Find(ctx)
	} else {
		records, err = gorm.G[T](r.Ctx(ctx)).Find(ctx)
	}
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return records, nil
}

// Transaction 开启事务并把事务实例放进 context，回调内用同一 ctx 调仓储即在同一事务里
func (r *BaseRepository[T]) Transaction(ctx context.Context, fn func(ctx context.Context) error) error {
	return errs.Wrap(r.Ctx(ctx).Transaction(func(tx *gorm.DB) error {
		txCtx := context.WithValue(tx.Statement.Context, TxKey{}, tx)
		return fn(txCtx)
	}))
}

// orderBy 把客户端排序参数转成 clause.OrderBy
func orderBy(param *result.QueryParam) clause.OrderBy {
	columns := make([]clause.OrderByColumn, 0, len(param.Sorts))
	for _, sort := range param.Sorts {
		columns = append(columns, clause.OrderByColumn{
			Column: clause.Column{Name: util.CamelToSnake(sort.Field)},
			Desc:   sort.Direction == "desc",
		})
	}
	return clause.OrderBy{Columns: columns}
}

// Page 只处理分页：计数、排序、偏移与结果组装
//
// - query 条件链，不包含查询和分页
// - pageParam 分页和排序参数
func (r *BaseRepository[T]) Page(
	ctx context.Context,
	query gorm.ChainInterface[T],
	pageParam *result.QueryParam,
) (*result.PageResult[T], error) {
	if pageParam == nil {
		pageParam = &result.QueryParam{}
	}
	if err := pageParam.Valid(); err != nil {
		return nil, err
	}

	total, err := query.Count(ctx, "*")
	if err != nil {
		return nil, errs.Wrap(err)
	}
	if total == 0 {
		return result.NewPageResult([]T{}, 0, pageParam.GetPageIndex(), pageParam.GetPageSize()), nil
	}

	if pageParam.HasSort() {
		query = query.Order(orderBy(pageParam))
	}
	if pageParam.HasPage() {
		// 翻页必须有确定顺序，否则同一批数据可能出现在相邻两页
		if !pageParam.HasSort() {
			query = query.Order(clause.OrderBy{
				Columns: []clause.OrderByColumn{{Column: clause.Column{Name: "id"}, Desc: true}},
			})
		}
		query = query.Offset(pageParam.GetOffset()).Limit(pageParam.GetPageSize())
	}
	records, err := query.Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return result.NewPageResult(records, int(total), pageParam.GetPageIndex(), pageParam.GetPageSize()), nil
}
