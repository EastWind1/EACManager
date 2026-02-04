package repository

import (
	"backend-go/internal/common/database"
	"backend-go/internal/common/result"
	"backend-go/internal/reimburse/model"
	"context"
	"errors"

	"gorm.io/gorm"
)

type ReimburseRepository struct {
	*database.BaseRepository[model.Reimbursement]
}

func NewReimburseRepository(db *gorm.DB) *ReimburseRepository {
	return &ReimburseRepository{
		BaseRepository: database.NewBaseRepository[model.Reimbursement](db),
	}
}

// ExistsByNumber 是否存在对应单号
func (r *ReimburseRepository) ExistsByNumber(ctx context.Context, number string) (bool, error) {
	var count int64
	err := r.GetDB(ctx).Model(&model.Reimbursement{}).Where("number = ?", number).Count(&count).Error
	return count > 0, err
}

// FindFullById 查询完整实体
func (r *ReimburseRepository) FindFullById(ctx context.Context, id uint) (*model.Reimbursement, error) {
	var res model.Reimbursement
	if err := r.GetDB(ctx).Preload("Details").Find(&res, "reimbursement.id = ?", id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &res, nil
}

// Updates 更新
func (r *ReimburseRepository) Updates(ctx context.Context, entity *model.Reimbursement) error {
	if err := r.GetDB(ctx).Session(&gorm.Session{FullSaveAssociations: true}).Save(entity).Error; err != nil {
		return err
	}
	return nil
}

// Delete 删除整个实体
func (r *ReimburseRepository) Delete(ctx context.Context, entity *model.Reimbursement) error {
	res := r.GetDB(ctx).Select("Details").Delete(entity)
	return res.Error
}

// FindByParam 根据查询条件查询
func (r *ReimburseRepository) FindByParam(ctx context.Context, param *model.ReimburseQueryParam) (*result.PageResult[model.Reimbursement], error) {
	q := r.GetDB(ctx).Model(&model.Reimbursement{}).WithContext(ctx)
	if param.Number != "" {
		q = q.Where("number = ?", param.Number)
	}
	if param.Summary != "" {
		q = q.Where("summary like %?%", param.Summary)
	}
	if param.States != nil && len(param.States) > 0 {
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
		return nil, res.Error
	}
	q, err := r.BuildQueryWithParam(q, &param.QueryParam)
	var reimbursements []model.Reimbursement
	if err = q.Find(&reimbursements).Error; err != nil {
		return nil, err
	}
	return result.NewPageResult(&reimbursements, int(count), *param.PageIndex, *param.PageSize), nil
}
