package bill

import (
	"backend-go/pkg/cache"
	"backend-go/pkg/result"
	"context"
	"fmt"
	"time"
)

const amountCacheName = "bill-amount"

type StatisticService struct {
	cache    cache.Cache
	billRepo *Repository
	querySrv *QueryService
	ttl      time.Duration
}

func newStatisticService(cache cache.Cache, billRepo *Repository, querySrv *QueryService, ttl time.Duration) *StatisticService {
	return &StatisticService{
		cache:    cache,
		billRepo: billRepo,
		querySrv: querySrv,
		ttl:      ttl,
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
	var res []result.MonthSumAmount
	if s.cache.GetJSON(amountCacheName, key, &res) {
		return res, nil
	}
	res, err = s.billRepo.SumReceiveAmountByMonth(ctx, scope)
	if err != nil {
		return nil, err
	}
	// 仪表盘金额允许短时间不一致：只靠 TTL 刷新，写操作不主动清空。
	s.cache.SetJSON(amountCacheName, key, res, s.ttl)
	return res, nil
}
