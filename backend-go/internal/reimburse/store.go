package reimburse

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"errors"
	"fmt"

	"gorm.io/gorm"
)

type Repository struct {
	*database.BaseRepository[Reimbursement]
}

func NewReimburseRepository(db *gorm.DB) *Repository {
	return &Repository{
		BaseRepository: database.NewBaseRepository[Reimbursement](db),
	}
}

// ExistsByNumber 是否存在对应单号
func (r *Repository) ExistsByNumber(ctx context.Context, number string) (bool, error) {
	var count int64
	err := r.GetDB(ctx).Model(&Reimbursement{}).Where("number = ?", number).Count(&count).Error
	return count > 0, errs.Wrap(err)
}

// FindFullById 查询完整实体
func (r *Repository) FindFullById(ctx context.Context, id uint) (*Reimbursement, error) {
	var res Reimbursement
	if err := r.GetDB(ctx).Preload("Details").Where("reimbursement.id = ?", id).Take(&res).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &res, nil
}

// Updates 更新
func (r *Repository) Updates(ctx context.Context, entity *Reimbursement) error {
	res := r.GetDB(ctx).Session(&gorm.Session{FullSaveAssociations: true}).Save(entity)
	if res.Error != nil {
		return errs.Wrap(res.Error)
	}
	if res.RowsAffected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return nil
}

// Delete 删除整个实体
func (r *Repository) Delete(ctx context.Context, entity *Reimbursement) error {
	res := r.GetDB(ctx).Select("Details").Delete(entity)
	if res.Error != nil {
		return errs.Wrap(res.Error)
	}
	if res.RowsAffected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return nil
}

// FindByParam 根据查询条件查询
func (r *Repository) FindByParam(ctx context.Context, param *QueryParam) (*result.PageResult[Reimbursement], error) {
	q := r.GetDB(ctx).Model(&Reimbursement{}).WithContext(ctx)
	if param.Number != "" {
		q = q.Where("number = ?", param.Number)
	}
	if param.Summary != "" {
		q = q.Where("summary like ?", fmt.Sprintf("%%%s%%", param.Summary))
	}
	if len(param.States) > 0 {
		q = q.Where("state in (?)", param.States)
	}
	if param.ReimburseStartDate != nil {
		q = q.Where("reimburse_date >= ?", param.ReimburseStartDate)
	}
	if param.ReimburseEndDate != nil {
		q = q.Where("reimburse_date <= ?", param.ReimburseEndDate)
	}
	var count int64
	res := q.Count(&count)
	if res.Error != nil {
		return nil, errs.Wrap(res.Error)
	}
	q, err := r.BuildQueryWithParam(q, &param.QueryParam)
	if err != nil {
		return nil, err
	}
	var reimbursements []Reimbursement
	if err := q.Find(&reimbursements).Error; err != nil {
		return nil, errs.Wrap(err)
	}
	return result.NewPageResult(reimbursements, int(count), *param.PageIndex, *param.PageSize), nil
}
