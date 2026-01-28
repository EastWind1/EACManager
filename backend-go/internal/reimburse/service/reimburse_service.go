package service

import (
	error2 "backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"backend-go/internal/reimburse/model"
	"backend-go/internal/reimburse/repository"
	"fmt"
	"math/rand"
	"time"
)

type ReimburseService struct {
	reimburseRepo   *repository.ReimburseRepository
	reimburseMapper *ReimburseMapper
}

type ReimburseMapper struct{}

func NewReimburseService(reimburseRepo *repository.ReimburseRepository) *ReimburseService {
	return &ReimburseService{
		reimburseRepo:   reimburseRepo,
		reimburseMapper: &ReimburseMapper{},
	}
}

func (s *ReimburseService) GenerateNumber() string {
	timestamp := time.Now().Format("20060102")
	randomNum := rand.Intn(1000)
	return fmt.Sprintf("R%s%04d", timestamp, randomNum)
}

func (s *ReimburseService) FindByID(id int) (*model.ReimbursementDTO, error) {
	if id == 0 {
		return nil, error2.NewBizError("ID不能为空")
	}
	bill, err := s.reimburseRepo.FindByID(id)
	if err != nil {
		return nil, error2.NewBizError("单据不存在")
	}
	dto := bill.ToDTO()
	return &dto, nil
}

func (s *ReimburseService) Create(dto *model.ReimbursementDTO) (*model.ReimbursementDTO, error) {
	if dto.ID != 0 {
		exists, _ := s.reimburseRepo.ExistsByID(dto.ID)
		if exists {
			return nil, error2.NewBizError("单据已存在")
		}
	}

	if dto.Number == "" {
		dto.Number = s.GenerateNumber()
	} else {
		exists, _ := s.reimburseRepo.ExistsByNumber(dto.Number)
		if exists {
			return nil, error2.NewBizError("单据编号已存在")
		}
	}

	bill := s.reimburseMapper.ToEntity(dto)
	if err := s.reimburseRepo.Create(bill); err != nil {
		return nil, err
	}

	result := bill.ToDTO()
	return &result, nil
}

func (s *ReimburseService) Update(dto *model.ReimbursementDTO) (*model.ReimbursementDTO, error) {
	if dto.ID == 0 {
		return nil, error2.NewBizError("id 不能为空")
	}

	bill, err := s.reimburseRepo.FindByID(dto.ID)
	if err != nil {
		return nil, error2.NewBizError("单据不存在")
	}

	if bill.Number != dto.Number {
		return nil, error2.NewBizError("单号不能修改")
	}

	bill.State = dto.State
	bill.Summary = dto.Summary
	bill.TotalAmount = dto.TotalAmount
	bill.ReimburseDate = dto.ReimburseDate
	bill.Remark = dto.Remark

	bill.Details = make([]model.ReimburseDetail, 0, len(dto.Details))
	for _, detailDTO := range dto.Details {
		detail := model.ReimburseDetail{
			ReimbursementID: bill.ID,
			Name:            detailDTO.Name,
			Amount:          detailDTO.Amount,
		}
		if detailDTO.ID != 0 {
			detail.ID = detailDTO.ID
		}
		bill.Details = append(bill.Details, detail)
	}

	if err := s.reimburseRepo.Update(bill); err != nil {
		return nil, err
	}

	result := bill.ToDTO()
	return &result, nil
}

func (s *ReimburseService) Delete(ids []int) (*result.ActionsResult[int, any], error) {
	res := &result.ActionsResult[int, any]{Results: make([]result.Row[int, any], 0, len(ids))}

	for _, id := range ids {
		if id == 0 {
			continue
		}

		bill, err := s.reimburseRepo.FindByID(id)
		if err != nil {
			res.Results = append(res.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		if bill.State != model.ReimburseStateCreated {
			res.Results = append(res.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		if err := s.reimburseRepo.Delete(id); err != nil {
			res.Results = append(res.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		res.Results = append(res.Results, result.Row[int, any]{
			Param: id,
			Error: false,
		})
	}

	return res, nil
}

func (s *ReimburseService) Process(ids []int) (*result.ActionsResult[int, any], error) {
	result := &result.ActionsResult[int, any]{Results: make([]result.Row[int, any], 0, len(ids))}

	for _, id := range ids {
		if id == 0 {
			continue
		}

		bill, err := s.reimburseRepo.FindByID(id)
		if err != nil {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		if bill.State != model.ReimburseStateCreated {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		bill.State = model.ReimburseStateProcessing
		if err := s.reimburseRepo.Update(bill); err != nil {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		result.Results = append(result.Results, result.Row[int, any]{
			Param: id,
			Error: false,
		})
	}

	return result, nil
}

func (s *ReimburseService) Finish(ids []int) (*result.ActionsResult[int, any], error) {
	result := &result.ActionsResult[int, any]{Results: make([]result.Row[int, any], 0, len(ids))}

	for _, id := range ids {
		if id == 0 {
			continue
		}

		bill, err := s.reimburseRepo.FindByID(id)
		if err != nil {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		if bill.State != model.ReimburseStateProcessing {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		bill.State = model.ReimburseStateFinished
		if err := s.reimburseRepo.Update(bill); err != nil {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		result.Results = append(result.Results, result.Row[int, any]{
			Param: id,
			Error: false,
		})
	}

	return result, nil
}

func (m *ReimburseMapper) ToEntity(dto *model.ReimbursementDTO) *model.Reimbursement {
	bill := &model.Reimbursement{
		ID:            dto.ID,
		Number:        dto.Number,
		State:         dto.State,
		Summary:       dto.Summary,
		TotalAmount:   dto.TotalAmount,
		ReimburseDate: dto.ReimburseDate,
		Remark:        dto.Remark,
	}

	bill.Details = make([]model.ReimburseDetail, 0, len(dto.Details))
	for _, detailDTO := range dto.Details {
		detail := model.ReimburseDetail{
			ID:     detailDTO.ID,
			Name:   detailDTO.Name,
			Amount: detailDTO.Amount,
		}
		bill.Details = append(bill.Details, detail)
	}

	return bill
}
