package reimburse

import (
	"backend-go/pkg/cache"
	"backend-go/pkg/result"
	"context"
	"time"
)

// StatisticService 报销单统计服务
type StatisticService struct {
	cache         cache.Cache
	reimburseRepo *Repository
}

func NewStatisticService(cache cache.Cache, reimburseRepo *Repository) *StatisticService {
	return &StatisticService{
		cache:         cache,
		reimburseRepo: reimburseRepo,
	}
}

// CountReimburseByState 统计不同状态的报销单数量
func (s *StatisticService) CountReimburseByState(ctx context.Context) (*result.CountByStateResult, error) {
	states := []State{
		Created,
		Processing,
		Finished,
	}

	res, err := s.reimburseRepo.CountByState(ctx, states)
	if err != nil {
		return nil, err
	}
	return res, nil
}

// SumAmountByMonth 按月份统计报销金额总和
func (s *StatisticService) SumAmountByMonth(ctx context.Context) ([]result.MonthSumAmount, error) {
	if value, ok := s.cache.Get("reimburse", "SumAmountByMonth"); ok {
		return value.([]result.MonthSumAmount), nil
	}
	res, err := s.reimburseRepo.SumAmountByMonth(ctx)
	if err != nil {
		return nil, err
	}
	s.cache.PutWithExpire("reimburse", "SumAmountByMonth", res, time.Hour)
	return res, nil
}
