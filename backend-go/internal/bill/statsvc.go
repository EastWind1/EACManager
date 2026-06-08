package bill

import (
	"backend-go/pkg/cache"
	"context"
	"time"
)

// StatisticService 统计服务
type StatisticService struct {
	cache    cache.Cache
	billRepo *Repository
}

func NewStatisticService(cache cache.Cache, billRepo *Repository) *StatisticService {
	return &StatisticService{
		cache:    cache,
		billRepo: billRepo,
	}
}

// CountBillsByState 统计不同状态的服务单据数量
func (s *StatisticService) CountBillsByState(ctx context.Context) (*CountByStateResult, error) {
	states := []State{
		Created,
		Processing,
		Processed,
	}

	res, err := s.billRepo.CountByState(ctx, states)
	if err != nil {
		return nil, err
	}
	return res, nil
}

// SumAmountByMonth 按月份统计应收和已收服务单据金额总和
func (s *StatisticService) SumAmountByMonth(ctx context.Context) ([]MonthSumAmount, error) {
	if value, ok := s.cache.Get("service-bill", "SumAmountByMonth"); ok {
		return value.([]MonthSumAmount), nil
	}
	res, err := s.billRepo.SumReceiveAmountByMonth(ctx)
	if err != nil {
		return nil, err
	}
	s.cache.PutWithExpire("service-bill", "SumAmountByMonth", res, time.Hour)
	return res, nil
}
