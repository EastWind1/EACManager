package service

import (
	"backend-go/internal/servicebill/model"
	"backend-go/internal/servicebill/repository"
	"context"
)

// StatisticService 统计服务
type StatisticService struct {
	billRepo *repository.ServiceBillRepository
}

func NewStatisticService(billRepo *repository.ServiceBillRepository) *StatisticService {
	return &StatisticService{
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
	return s.billRepo.SumReceiveAmountByMonth(ctx)
}
