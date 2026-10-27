package bill

import (
	"backend-go/pkg/cache"
	"backend-go/pkg/result"
	"context"
	"time"
)

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

func (s *StatisticService) CountBillsByState(ctx context.Context) (*result.CountByStateResult, error) {
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

func (s *StatisticService) SumAmountByMonth(ctx context.Context) ([]result.MonthSumAmount, error) {
	if value, ok := s.cache.Get("service-bill", "SumAmountByMonth"); ok {
		return value.([]result.MonthSumAmount), nil
	}
	res, err := s.billRepo.SumReceiveAmountByMonth(ctx)
	if err != nil {
		return nil, err
	}
	s.cache.PutWithExpire("service-bill", "SumAmountByMonth", res, time.Hour)
	return res, nil
}
