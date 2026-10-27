package reimburse

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"errors"
	"fmt"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// 查询统一走 gorm.G[*Reimburse] / gorm.G[*Detail]：实体内嵌带钩子的 security.Audit，
// 指针形态才能让泛型链式方法正确带出模型并触发审计钩子。
type Repository struct {
	*database.BaseRepository[Reimburse]
}

func NewReimburseRepository(db *gorm.DB) *Repository {
	return &Repository{
		BaseRepository: database.NewBaseRepository[Reimburse](db),
	}
}

// Insert 新建单据，明细与主表同一条语句写入
func (r *Repository) Insert(ctx context.Context, entity *Reimburse) error {
	if err := gorm.G[Reimburse](r.Ctx(ctx)).Create(ctx, entity); err != nil {
		if database.IsDuplicateKey(err) {
			return errs.NewBizError("单据编号已存在")
		}
		return errs.Wrap(err)
	}
	return nil
}

// FindByID 查询报销单主表（不含明细），状态流转只需要主表字段
func (r *Repository) FindByID(ctx context.Context, id uint) (*Reimburse, error) {
	record, err := gorm.G[*Reimburse](r.Ctx(ctx)).
		Select("id", "number", "state", "total_amount", "reimburse_date", "version").
		Where("id = ?", id).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return record, nil
}

func (r *Repository) FindFullById(ctx context.Context, id uint) (*Reimburse, error) {
	record, err := gorm.G[*Reimburse](r.Ctx(ctx)).Preload("Details", nil).Where("id = ?", id).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return record, nil
}

// FindAllWithDetails 批量查询完整实体（导出）：主表与明细各一条语句，与单据数量无关
func (r *Repository) FindAllWithDetails(ctx context.Context, ids []uint) ([]Reimburse, error) {
	if len(ids) == 0 {
		return []Reimburse{}, nil
	}
	pointers, err := gorm.G[*Reimburse](r.Ctx(ctx)).Where("id IN ?", ids).Order("id").Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	reimbursements := make([]Reimburse, len(pointers))
	for i, p := range pointers {
		reimbursements[i] = *p
	}
	if len(reimbursements) == 0 {
		return reimbursements, nil
	}

	details, err := gorm.G[*Detail](r.Ctx(ctx)).Where("reimbursement_id IN ?", ids).Order("id").Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	byReimburse := make(map[uint][]Detail, len(reimbursements))
	for _, detail := range details {
		id := uint(detail.ReimbursementID)
		byReimburse[id] = append(byReimburse[id], *detail)
	}
	for i := range reimbursements {
		reimbursements[i].Details = byReimburse[reimbursements[i].ID]
	}
	return reimbursements, nil
}

// Updates 更新主表字段并同步明细（明细按 ID 对账：保留原位更新、孤儿删除、新增插入）
func (r *Repository) Updates(ctx context.Context, entity *Reimburse) error {
	affected, err := gorm.G[*Reimburse](r.Ctx(ctx)).Select(
		"number", "state", "summary", "total_amount", "reimburse_date", "remark").
		Where("id = ?", entity.ID).Updates(ctx, entity)
	if err != nil {
		return errs.Wrap(err)
	}
	if affected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return r.syncDetails(ctx, entity)
}

// UpdateState 只更新状态字段
func (r *Repository) UpdateState(ctx context.Context, entity *Reimburse) error {
	affected, err := gorm.G[*Reimburse](r.Ctx(ctx)).Select("state").
		Where("id = ?", entity.ID).Updates(ctx, entity)
	if err != nil {
		return errs.Wrap(err)
	}
	if affected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return nil
}

// syncDetails 明细对账：删除孤儿、原地更新保留行、新增缺失行
func (r *Repository) syncDetails(ctx context.Context, entity *Reimburse) error {
	existing, err := gorm.G[*Detail](r.Ctx(ctx)).
		Select("id").Where("reimbursement_id = ?", entity.ID).Find(ctx)
	if err != nil {
		return errs.Wrap(err)
	}
	keep := make(map[uint]bool, len(entity.Details))
	toUpdate := make([]Detail, 0, len(entity.Details))
	toInsert := make([]Detail, 0, len(entity.Details))
	for _, detail := range entity.Details {
		detail.ReimbursementID = int(entity.ID)
		if detail.ID == 0 {
			toInsert = append(toInsert, detail)
			continue
		}
		keep[detail.ID] = true
		toUpdate = append(toUpdate, detail)
	}

	remove := make([]uint, 0, len(existing))
	for _, detail := range existing {
		if !keep[detail.ID] {
			remove = append(remove, detail.ID)
		}
	}
	if len(remove) > 0 {
		if _, err = gorm.G[Detail](r.Ctx(ctx)).Where("id IN ?", remove).Delete(ctx); err != nil {
			return errs.Wrap(err)
		}
	}
	if len(toUpdate) > 0 {
		err = gorm.G[Detail](r.Ctx(ctx), clause.OnConflict{
			Columns:   []clause.Column{{Name: "id"}},
			DoUpdates: clause.AssignmentColumns([]string{"reimbursement_id", "name", "amount"}),
		}).CreateInBatches(ctx, &toUpdate, relationBatchSize)
		if err != nil {
			return errs.Wrap(err)
		}
	}
	if len(toInsert) > 0 {
		if err = gorm.G[Detail](r.Ctx(ctx)).CreateInBatches(ctx, &toInsert, relationBatchSize); err != nil {
			return errs.Wrap(err)
		}
	}
	entity.Details = append(toUpdate, toInsert...)
	return nil
}

func (r *Repository) Delete(ctx context.Context, id uint) (bool, error) {
	affected, err := gorm.G[*Reimburse](r.Ctx(ctx)).Select("Details").Where("id = ?", id).Delete(ctx)
	if err != nil {
		return false, errs.Wrap(err)
	}
	return affected > 0, nil
}

// FindByParam 只负责拼查询链；计数、排序与分页由 BaseRepository.Page 统一处理。
func (r *Repository) FindByParam(ctx context.Context, param *QueryParam) (*result.PageResult[Reimburse], error) {
	// 条件全部通过泛型链添加：gorm.G 会重置语句，挂在 *gorm.DB 上的条件不会生效
	g := r.Chain(ctx)
	if param.Number != "" {
		g = g.Where("number = ?", param.Number)
	}
	if param.Summary != "" {
		g = g.Where("summary LIKE ?", fmt.Sprintf("%%%s%%", param.Summary))
	}
	if len(param.States) > 0 {
		g = g.Where("state IN ?", param.States)
	}
	if param.ReimburseStartDate != nil {
		g = g.Where("reimburse_date >= ?", param.ReimburseStartDate)
	}
	if param.ReimburseEndDate != nil {
		g = g.Where("reimburse_date <= ?", param.ReimburseEndDate)
	}

	return r.Page(ctx, g, &param.QueryParam)
}
