package repository

import (
	"backend-go/internal/common/database"
	"backend-go/internal/common/result"
	"backend-go/internal/servicebill/model"
	"context"
	"errors"
	"fmt"
	"sort"
	"time"

	"gorm.io/gorm"
)

type ServiceBillRepository struct {
	*database.BaseRepository[model.ServiceBill]
}

func NewServiceBillRepository(db *gorm.DB) *ServiceBillRepository {
	return &ServiceBillRepository{
		BaseRepository: database.NewBaseRepository[model.ServiceBill](db),
	}
}

// ExistsByNumber 是否存在对应单号
func (r *ServiceBillRepository) ExistsByNumber(ctx context.Context, number string) (bool, error) {
	var count int64
	err := r.GetDB(ctx).Model(&model.ServiceBill{}).Where("number = ?", number).Count(&count).Error
	return count > 0, err
}

// FindFullById 查询完整实体
func (r *ServiceBillRepository) FindFullById(ctx context.Context, id uint) (*model.ServiceBill, error) {
	var res model.ServiceBill
	if err := r.GetDB(ctx).Joins("company").Joins("service_bill_detail").First(&res, "id = ?", id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &res, nil
}

// FindByParam 根据查询条件查询
func (r *ServiceBillRepository) FindByParam(ctx context.Context, param *model.ServiceBillQueryParam) (*result.PageResult[model.ServiceBill], error) {
	q := r.GetDB(ctx).WithContext(ctx)
	if param.Number != "" {
		q = q.Where("number = ?", param.Number)
	}
	if param.States != nil && len(param.States) > 0 {
		q = q.Where("state in (?)", param.States)
	}
	if param.ProjectName != "" {
		q = q.Where("project_name like %?%", param.ProjectName)
	}
	if param.OrderStartDate != nil {
		q = q.Where("reimburse_date >= ?", param.OrderStartDate)
	}
	if param.OrderEndDate != nil {
		q = q.Where("reimburse_date <= ?", param.OrderEndDate)
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
		return nil, res.Error
	}
	q, err := r.BuildQueryWithParam(q, &param.QueryParam)
	var serviceBills []model.ServiceBill
	if err = q.Find(&serviceBills).Error; err != nil {
		return nil, err
	}
	return result.NewPageResult(&serviceBills, int(count), *param.PageIndex, *param.PageSize), nil
}

// CountByState 根据单据状态计数
func (r *ServiceBillRepository) CountByState(ctx context.Context, states []model.ServiceBillState) (*model.CountByStateResult, error) {
	res := make(model.CountByStateResult)
	if err := r.GetDB(ctx).Select("state, count(1)").
		Table("service_bill").
		Where("state in (?)", states).
		Group("state").Find(&res).Error; err != nil {
		return nil, err
	}
	return &res, nil
}

// SumReceiveAmountByMonth 根据月份分组统计一年的收入
func (r *ServiceBillRepository) SumReceiveAmountByMonth(ctx context.Context) (*[]model.MonthSumAmount, error) {
	var qRes []model.YearMonthSumAmount
	now := time.Now()
	end := time.Date(now.Year(), now.Month()+1, 1, 23, 59, 59, 59, now.Location()).AddDate(0, 0, -1)
	start := time.Date(now.Year()-1, now.Month(), 1, 0, 0, 0, 0, now.Location())
	err := r.GetDB(ctx).
		Select("extract(YEAR from processed_date) as year, extract(MONTH from processed_date) as month, SUM(total_amount) as sum").
		Where("state IN ?", []model.ServiceBillState{model.ServiceBillStateProcessed, model.ServiceBillStateFinished}).
		Where("processed_date IS NOT NULL").
		Where("processed_date BETWEEN ? AND ?", start, end).
		Group("extract(YEAR from processed_date), extract(MONTH from processed_date)").
		Find(&qRes).Error

	if err != nil {
		return nil, err
	}

	var res []model.MonthSumAmount
	for _, row := range qRes {
		res = append(res, model.MonthSumAmount{
			Month:  fmt.Sprintf("%v-%v", row.Year, row.Month),
			Amount: row.Amount,
		})
	}
	sort.Slice(res, func(i, j int) bool {
		return res[i].Month < res[j].Month
	})

	return &res, nil
}
