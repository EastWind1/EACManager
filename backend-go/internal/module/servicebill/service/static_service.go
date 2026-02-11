package service

import (
	model2 "backend-go/internal/module/servicebill/model"
	"backend-go/internal/module/servicebill/repository"
	"backend-go/internal/pkg/cache"
	"context"
	"time"
)

// StatisticService 统计服务
type StatisticService struct {
	cache    cache.Cache
	billRepo *repository.ServiceBillRepository
}

func NewStatisticService(cache cache.Cache, billRepo *repository.ServiceBillRepository) *StatisticService {
	return &StatisticService{
		cache:    cache,
		billRepo: billRepo,
	}
}

// CountBillsByState 统计不同状态的服务单据数量
func (s *StatisticService) CountBillsByState(ctx context.Context) (*model2.CountByStateResult, error) {
	states := []model2.ServiceBillState{
		model2.ServiceBillStateCreated,
		model2.ServiceBillStateProcessing,
		model2.ServiceBillStateProcessed,
	}

	res, err := s.billRepo.CountByState(ctx, states)
	if err != nil {
		return nil, err
	}
	return res, nil
}

// SumAmountByMonth 按月份统计应收和已收服务单据金额总和
func (s *StatisticService) SumAmountByMonth(ctx context.Context) ([]model2.MonthSumAmount, error) {
	if value, ok := s.cache.Get("service-bill", "SumAmountByMonth"); ok {
		return value.([]model2.MonthSumAmount), nil
	}
	res, err := s.billRepo.SumReceiveAmountByMonth(ctx)
	if err != nil {
		return nil, err
	}
	s.cache.PutWithExpire("service-bill", "SumAmountByMonth", res, time.Hour)
	return res, nil
}
