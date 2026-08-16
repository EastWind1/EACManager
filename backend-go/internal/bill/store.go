package bill

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
	*database.BaseRepository[ServiceBill]
}

func NewRepository(db *gorm.DB) *Repository {
	return &Repository{
		BaseRepository: database.NewBaseRepository[ServiceBill](db),
	}
}

// ExistsByNumber 是否存在对应单号
func (r *Repository) ExistsByNumber(ctx context.Context, number string) (bool, error) {
	var count int64
	err := r.GetDB(ctx).Model(&ServiceBill{}).Where("number = ?", number).Count(&count).Error
	return count > 0, errs.Wrap(err)
}

// FindFullById 查询完整实体
func (r *Repository) FindFullById(ctx context.Context, id uint) (*ServiceBill, error) {
	var res ServiceBill
	if err := r.GetDB(ctx).Model(&res).Preload("ProductCompany").Preload("Details").
		Where("id = ?", id).
		Take(&res).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &res, nil
}

// FindByParam 根据查询条件查询
func (r *Repository) FindByParam(ctx context.Context, param *ServiceBillQueryParam) (*result.PageResult[ServiceBill], error) {
	q := r.GetDB(ctx).WithContext(ctx).Model(&ServiceBill{})
	if param.Number != "" {
		q = q.Where("number = ?", param.Number)
	}
	if len(param.States) > 0 {
		q = q.Where("state in (?)", param.States)
	}
	if param.ProjectName != "" {
		q = q.Where("project_name like ?", fmt.Sprintf("%%%s%%", param.ProjectName))
	}
	if param.OrderStartDate != nil {
		q = q.Where("order_date >= ?", param.OrderStartDate)
	}
	if param.OrderEndDate != nil {
		q = q.Where("order_date <= ?", param.OrderEndDate)
	}
	if param.ProcessedStartDate != nil {
		q = q.Where("processed_date >= ?", param.ProcessedStartDate)
	}
	if param.ProcessedEndDate != nil {
		q = q.Where("processed_date <= ?", param.ProcessedEndDate)
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
	var serviceBills []ServiceBill
	if err = q.Find(&serviceBills).Error; err != nil {
		return nil, errs.Wrap(err)
	}
	return result.NewPageResult(serviceBills, int(count), *param.PageIndex, *param.PageSize), nil
}

// Updates 更新
func (r *Repository) Updates(ctx context.Context, entity *ServiceBill) error {
	res := r.GetDB(ctx).Session(&gorm.Session{FullSaveAssociations: true}).Omit("ProductCompany").Save(entity)
	if res.Error != nil {
		return errs.Wrap(res.Error)
	}
	if res.RowsAffected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return nil
}

// Delete 删除整个实体
func (r *Repository) Delete(ctx context.Context, entity *ServiceBill) error {
	res := r.GetDB(ctx).Select("Details").Delete(entity)
	if res.Error != nil {
		return errs.Wrap(res.Error)
	}
	if res.RowsAffected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return nil
}
