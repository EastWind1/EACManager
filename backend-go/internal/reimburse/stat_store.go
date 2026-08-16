package reimburse

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"fmt"
	"sort"
	"time"
)

// CountByState 根据报销单状态计数
func (r *Repository) CountByState(ctx context.Context, states []State) (*result.CountByStateResult, error) {
	rows := make([]result.CountByStateRow, 0)
	if err := r.GetDB(ctx).Select("state, count(1)").
		Table("reimbursement").
		Where("state in (?)", states).
		Group("state").Find(&rows).Error; err != nil {
		return nil, errs.Wrap(err)
	}

	res := make(result.CountByStateResult)
	for _, row := range rows {
		state := State(row.State)
		res[state.String()] = row.Count
	}
	return &res, nil
}

// SumAmountByMonth 根据报销日期分组统计近一年的报销金额
func (r *Repository) SumAmountByMonth(ctx context.Context) ([]result.MonthSumAmount, error) {
	var qRes []result.YearMonthSumAmount
	now := time.Now()
	end := time.Date(now.Year(), now.Month()+1, 1, 23, 59, 59, 59, now.Location()).AddDate(0, 0, -1)
	start := time.Date(now.Year()-1, now.Month(), 1, 0, 0, 0, 0, now.Location())
	err := r.GetDB(ctx).
		Select("extract(YEAR from reimburse_date) as year, extract(MONTH from reimburse_date) as month, SUM(total_amount) as amount").
		Table("reimbursement").
		Where("reimburse_date IS NOT NULL").
		Where("reimburse_date BETWEEN ? AND ?", start, end).
		Group("extract(YEAR from reimburse_date), extract(MONTH from reimburse_date)").
		Find(&qRes).Error

	if err != nil {
		return nil, errs.Wrap(err)
	}
	sort.Slice(qRes, func(i, j int) bool {
		if qRes[i].Year == qRes[j].Year {
			return qRes[i].Month < qRes[j].Month
		}
		return qRes[i].Year < qRes[j].Year
	})
	var res []result.MonthSumAmount
	for _, row := range qRes {
		res = append(res, result.MonthSumAmount{
			Month:  fmt.Sprintf("%v-%v", row.Year, row.Month),
			Amount: row.Amount,
		})
	}
	return res, nil
}
