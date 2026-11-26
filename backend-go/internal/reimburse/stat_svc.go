package reimburse

import (
	"backend-go/pkg/cache"
	"backend-go/pkg/result"
	"context"
	"time"
)

const amountCacheName = "reimburse-amount"

type StatisticService struct {
	cache         cache.Cache
	reimburseRepo *Repository
	ttl           time.Duration
}

func newStatisticService(cache cache.Cache, reimburseRepo *Repository, ttl time.Duration) *StatisticService {
	return &StatisticService{
		cache:         cache,
		reimburseRepo: reimburseRepo,
		ttl:           ttl,
	}
}

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

func (s *StatisticService) SumAmountByMonth(ctx context.Context) ([]result.MonthSumAmount, error) {
	var res []result.MonthSumAmount
	if s.cache.GetJSON(amountCacheName, "SumAmountByMonth", &res) {
		return res, nil
	}
	res, err := s.reimburseRepo.SumAmountByMonth(ctx)
	if err != nil {
		return nil, err
	}
	// 仪表盘金额允许短时间不一致：只靠 TTL 刷新，写操作不主动清空。
	s.cache.SetJSON(amountCacheName, "SumAmountByMonth", res, s.ttl)
	return res, nil
}
