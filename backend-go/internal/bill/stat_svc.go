package bill

import (
	"backend-go/pkg/cache"
	"backend-go/pkg/result"
	"context"
	"fmt"
	"time"
)

type StatisticService struct {
	cache    cache.Cache
	billRepo *Repository
	querySrv *QueryService
}

func newStatisticService(cache cache.Cache, billRepo *Repository, querySrv *QueryService) *StatisticService {
	return &StatisticService{
		cache:    cache,
		billRepo: billRepo,
		querySrv: querySrv,
	}
}

func (s *StatisticService) CountBillsByState(ctx context.Context) (*result.CountByStateResult, error) {
	states := []State{
		Created,
		Processing,
		Processed,
	}

	scope, err := s.querySrv.Scope(ctx)
	if err != nil {
		return nil, err
	}
	res, err := s.billRepo.CountByState(ctx, states, scope)
	if err != nil {
		return nil, err
	}
	return res, nil
}

func (s *StatisticService) SumAmountByMonth(ctx context.Context) ([]result.MonthSumAmount, error) {
	scope, err := s.querySrv.Scope(ctx)
	if err != nil {
		return nil, err
	}
	key := "SumAmountByMonth"
	if scope != nil {
		key = fmt.Sprintf("SumAmountByMonth:user:%d", *scope)
	}
	if value, ok := s.cache.Get("service-bill", key); ok {
		return value.([]result.MonthSumAmount), nil
	}
	res, err := s.billRepo.SumReceiveAmountByMonth(ctx, scope)
	if err != nil {
		return nil, err
	}
	s.cache.PutWithExpire("service-bill", key, res, time.Hour)
	return res, nil
}
