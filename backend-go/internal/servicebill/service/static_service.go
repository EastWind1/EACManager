package service

import (
	"backend-go/internal/common/cache"
	"backend-go/internal/servicebill/model"
	"backend-go/internal/servicebill/repository"
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
func (s *StatisticService) CountBillsByState(ctx context.Context) (*model.CountByStateResult, error) {
	states := []model.ServiceBillState{
		model.ServiceBillStateCreated,
		model.ServiceBillStateProcessing,
		model.ServiceBillStateProcessed,
	}

	res, err := s.billRepo.CountByState(ctx, states)
	if err != nil {
		return nil, err
	}
	return res, nil
}

// SumAmountByMonth 按月份统计应收和已收服务单据金额总和
func (s *StatisticService) SumAmountByMonth(ctx context.Context) (*[]model.MonthSumAmount, error) {
	if value, ok := s.cache.Get("service-bill", "SumAmountByMonth"); ok {
		return value.(*[]model.MonthSumAmount), nil
	}
	res, err := s.billRepo.SumReceiveAmountByMonth(ctx)
	if err != nil {
		return nil, err
	}
	s.cache.PutWithExpire("service-bill", "SumAmountByMonth", res, time.Hour)
	return res, nil
}
