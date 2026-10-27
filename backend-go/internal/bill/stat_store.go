package bill

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"fmt"
	"time"

	"gorm.io/gorm"
)

func (r *Repository) CountByState(ctx context.Context, states []State) (*result.CountByStateResult, error) {
	rows, err := gorm.G[result.CountByStateRow](r.Ctx(ctx)).
		Table("service_bill").
		Select("state, count(1) AS count").
		Where("state IN ?", states).
		Group("state").
		Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}

	res := make(result.CountByStateResult, len(states))
	for _, row := range rows {
		state := State(row.State)
		res[state.String()] = row.Count
	}
	return &res, nil
}

func (r *Repository) SumReceiveAmountByMonth(ctx context.Context) ([]result.MonthSumAmount, error) {
	now := time.Now()
	end := time.Date(now.Year(), now.Month()+1, 1, 23, 59, 59, 59, now.Location()).AddDate(0, 0, -1)
	start := time.Date(now.Year()-1, now.Month(), 1, 0, 0, 0, 0, now.Location())
	// 聚合结果本来就只有十几行，排序交给数据库，省掉一次内存排序
	rows, err := gorm.G[result.YearMonthSumAmount](r.Ctx(ctx)).
		Table("service_bill").
		Select("extract(YEAR from processed_date) AS year, extract(MONTH from processed_date) AS month, SUM(total_amount) AS amount").
		Where("state IN ?", []State{Processed, Finished}).
		Where("processed_date IS NOT NULL").
		Where("processed_date BETWEEN ? AND ?", start, end).
		Group("extract(YEAR from processed_date), extract(MONTH from processed_date)").
		Order("year, month").
		Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}

	res := make([]result.MonthSumAmount, 0, len(rows))
	for _, row := range rows {
		res = append(res, result.MonthSumAmount{
			Month:  fmt.Sprintf("%v-%v", row.Year, row.Month),
			Amount: row.Amount,
		})
	}
	return res, nil
}
