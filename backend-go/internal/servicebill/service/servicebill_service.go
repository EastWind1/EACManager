package service

import (
	attachService "backend-go/internal/attach/service"
	error2 "backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	companyService "backend-go/internal/company/repository"
	"backend-go/internal/servicebill/model"
	"backend-go/internal/servicebill/repository"
	"fmt"
	"math"
	"time"
)

type ServiceBillService struct {
	billRepo      *repository.ServiceBillRepository
	billMapper    *ServiceBillMapper
	attachService *attachService.AttachmentService
	companyRepo   *companyService.CompanyRepository
}

type ServiceBillMapper struct{}

func NewServiceBillService(
	billRepo *repository.ServiceBillRepository,
	attachService *attachService.AttachmentService,
	companyRepo *companyService.CompanyRepository,
) *ServiceBillService {
	return &ServiceBillService{
		billRepo:      billRepo,
		billMapper:    &ServiceBillMapper{},
		attachService: attachService,
		companyRepo:   companyRepo,
	}
}

func (s *ServiceBillService) ValidateAmount(bill *model.ServiceBill) error {
	totalAmount := 0.0
	for _, detail := range bill.Details {
		expectedSubtotal := detail.Quantity * detail.UnitPrice
		if math.Abs(detail.Subtotal-expectedSubtotal) > 0.01 {
			return error2.NewBizError("明细金额有误")
		}
		totalAmount += detail.Subtotal
	}
	if math.Abs(totalAmount-bill.TotalAmount) > 0.01 {
		return error2.NewBizError("总金额有误")
	}
	return nil
}

func (s *ServiceBillService) GenerateNumber() string {
	timestamp := time.Now().Format("20060102")
	randomNum := time.Now().UnixNano() % 1000
	if randomNum < 0 {
		randomNum = -randomNum
	}
	return fmt.Sprintf("S%s%04d", timestamp, randomNum)
}

func (s *ServiceBillService) FindByID(id int) (*model.ServiceBillDTO, error) {
	if id == 0 {
		return nil, error2.NewBizError("ID不能为空")
	}
	bill, err := s.billRepo.FindByID(id)
	if err != nil {
		return nil, error2.NewBizError("单据不存在")
	}
	dto := bill.ToDTO()
	return &dto, nil
}

func (s *ServiceBillService) Create(dto *model.ServiceBillDTO) (*model.ServiceBillDTO, error) {
	if dto.ID != 0 {
		exists, _ := s.billRepo.ExistsByID(dto.ID)
		if exists {
			return nil, error2.NewBizError("单据已存在")
		}
	}

	if dto.Number == "" {
		dto.Number = s.GenerateNumber()
	} else {
		exists, _ := s.billRepo.ExistsByNumber(dto.Number)
		if exists {
			return nil, error2.NewBizError("单据编号已存在")
		}
	}

	bill := s.billMapper.ToEntity(dto)
	if err := s.ValidateAmount(bill); err != nil {
		return nil, err
	}

	if dto.ProductCompany != nil && dto.ProductCompany.ID != 0 {
		company, err := s.companyRepo.FindByID(dto.ProductCompany.ID)
		if err != nil {
			return nil, error2.NewBizError("公司不存在")
		}
		bill.ProductCompany = company
		bill.ProductCompanyID = &company.ID
	}

	if err := s.billRepo.Create(bill); err != nil {
		return nil, err
	}

	result := bill.ToDTO()
	return &result, nil
}

func (s *ServiceBillService) Update(dto *model.ServiceBillDTO) (*model.ServiceBillDTO, error) {
	if dto.ID == 0 {
		return nil, error2.NewBizError("id 不能为空")
	}

	bill, err := s.billRepo.FindByID(dto.ID)
	if err != nil {
		return nil, error2.NewBizError("单据不存在")
	}

	if bill.Number != dto.Number {
		return nil, error2.NewBizError("单号不能修改")
	}

	bill.Type = dto.Type
	bill.State = dto.State
	bill.ProjectName = dto.ProjectName
	bill.ProjectAddress = dto.ProjectAddress
	bill.ProjectContact = dto.ProjectContact
	bill.ProjectPhone = dto.ProjectPhone
	bill.OnSiteContact = dto.OnSiteContact
	bill.OnSitePhone = dto.OnSitePhone
	bill.ElevatorInfo = dto.ElevatorInfo
	bill.TotalAmount = dto.TotalAmount
	bill.Remark = dto.Remark
	bill.OrderDate = dto.OrderDate
	bill.ProcessedDate = dto.ProcessedDate
	bill.FinishedDate = dto.FinishedDate

	if dto.ProductCompany != nil && dto.ProductCompany.ID != 0 {
		company, err := s.companyRepo.FindByID(dto.ProductCompany.ID)
		if err != nil {
			return nil, error2.NewBizError("公司不存在")
		}
		bill.ProductCompany = company
		bill.ProductCompanyID = &company.ID
	} else {
		bill.ProductCompany = nil
		bill.ProductCompanyID = nil
	}

	bill.Details = make([]model.ServiceBillDetail, 0, len(dto.Details))
	for _, detailDTO := range dto.Details {
		detail := model.ServiceBillDetail{
			ServiceBillID: bill.ID,
			Device:        detailDTO.Device,
			Quantity:      detailDTO.Quantity,
			UnitPrice:     detailDTO.UnitPrice,
			Subtotal:      detailDTO.Subtotal,
			Remark:        detailDTO.Remark,
		}
		if detailDTO.ID != 0 {
			detail.ID = detailDTO.ID
		}
		bill.Details = append(bill.Details, detail)
	}

	if err := s.ValidateAmount(bill); err != nil {
		return nil, err
	}

	if err := s.billRepo.Update(bill); err != nil {
		return nil, err
	}

	result := bill.ToDTO()
	return &result, nil
}

func (s *ServiceBillService) Delete(ids []int) (*result.ActionsResult[int, any], error) {
	result := &result.ActionsResult[int, any]{Results: make([]result.Row[int, any], 0, len(ids))}

	for _, id := range ids {
		if id == 0 {
			continue
		}

		bill, err := s.billRepo.FindByID(id)
		if err != nil {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		if bill.State != model.ServiceBillStateCreated {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		if err := s.billRepo.Delete(id); err != nil {
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

func (s *ServiceBillService) Process(ids []int) (*result.ActionsResult[int, any], error) {
	result := &result.ActionsResult[int, any]{Results: make([]result.Row[int, any], 0, len(ids))}

	for _, id := range ids {
		if id == 0 {
			continue
		}

		bill, err := s.billRepo.FindByID(id)
		if err != nil {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		if bill.State != model.ServiceBillStateCreated {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		bill.State = model.ServiceBillStateProcessing
		if err := s.billRepo.Update(bill); err != nil {
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

func (s *ServiceBillService) Processed(ids []int, processedDate *string) (*result.ActionsResult[int, any], error) {
	result := &result.ActionsResult[int, any]{Results: make([]result.Row[int, any], 0, len(ids))}

	for _, id := range ids {
		if id == 0 {
			continue
		}

		bill, err := s.billRepo.FindByID(id)
		if err != nil {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		if bill.State != model.ServiceBillStateProcessing {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		bill.State = model.ServiceBillStateProcessed
		bill.ProcessedDate = processedDate
		if err := s.billRepo.Update(bill); err != nil {
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

func (s *ServiceBillService) Finish(ids []int, finishedDate *string) (*result.ActionsResult[int, any], error) {
	result := &result.ActionsResult[int, any]{Results: make([]result.Row[int, any], 0, len(ids))}

	for _, id := range ids {
		if id == 0 {
			continue
		}

		bill, err := s.billRepo.FindByID(id)
		if err != nil {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		if bill.State != model.ServiceBillStateProcessed {
			result.Results = append(result.Results, result.Row[int, any]{
				Param: id,
				Error: true,
			})
			continue
		}

		bill.State = model.ServiceBillStateFinished
		bill.FinishedDate = finishedDate
		if err := s.billRepo.Update(bill); err != nil {
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

func (m *ServiceBillMapper) ToEntity(dto *model.ServiceBillDTO) *model.ServiceBill {
	bill := &model.ServiceBill{
		ID:             dto.ID,
		Number:         dto.Number,
		Type:           dto.Type,
		State:          dto.State,
		ProjectName:    dto.ProjectName,
		ProjectAddress: dto.ProjectAddress,
		ProjectContact: dto.ProjectContact,
		ProjectPhone:   dto.ProjectPhone,
		OnSiteContact:  dto.OnSiteContact,
		OnSitePhone:    dto.OnSitePhone,
		ElevatorInfo:   dto.ElevatorInfo,
		TotalAmount:    dto.TotalAmount,
		OrderDate:      dto.OrderDate,
		ProcessedDate:  dto.ProcessedDate,
		FinishedDate:   dto.FinishedDate,
		Remark:         dto.Remark,
	}

	if dto.ProductCompany != nil {
		bill.ProductCompanyID = &dto.ProductCompany.ID
	}

	bill.Details = make([]model.ServiceBillDetail, 0, len(dto.Details))
	for _, detailDTO := range dto.Details {
		detail := model.ServiceBillDetail{
			ID:        detailDTO.ID,
			Device:    detailDTO.Device,
			Quantity:  detailDTO.Quantity,
			UnitPrice: detailDTO.UnitPrice,
			Subtotal:  detailDTO.Subtotal,
			Remark:    detailDTO.Remark,
		}
		bill.Details = append(bill.Details, detail)
	}

	return bill
}
