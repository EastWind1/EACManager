package model

import (
	"backend-go/internal/attach/model"
	"backend-go/internal/common/audit"
	"backend-go/internal/common/database"
	"backend-go/internal/common/errs"
	companyModel "backend-go/internal/company/model"
	"encoding/json"
	"time"

	"gorm.io/plugin/optimisticlock"
)

type ServiceBillState int

const (
	ServiceBillStateCreated ServiceBillState = iota
	ServiceBillStateProcessing
	ServiceBillStateProcessed
	ServiceBillStateFinished
)

func (s *ServiceBillState) String() string {
	return []string{"CREATED", "PROCESSING", "PROCESSED", "FINISHED"}[*s]
}

func (s *ServiceBillState) MarshalJSON() ([]byte, error) {
	str := ""
	switch *s {
	case ServiceBillStateCreated:
		str = "CREATED"
	case ServiceBillStateProcessing:
		str = "PROCESSING"
	case ServiceBillStateProcessed:
		str = "PROCESSED"
	case ServiceBillStateFinished:
		str = "FINISHED"

	}
	return json.Marshal(str)
}

func (s *ServiceBillState) UnmarshalJSON(data []byte) error {
	var str string
	if err := json.Unmarshal(data, &str); err == nil {
		switch str {
		case "CREATED":
			*s = ServiceBillStateCreated
		case "PROCESSING":
			*s = ServiceBillStateProcessing
		case "PROCESSED":
			*s = ServiceBillStateProcessed
		case "FINISHED":
			*s = ServiceBillStateFinished
		default:
			return errs.NewBizError("不支持的单据状态")
		}
	}
	return nil
}

type ServiceBillType int

const (
	ServiceBillTypeInstall ServiceBillType = iota
	ServiceBillTypeFix
)

func (s *ServiceBillType) MarshalJSON() ([]byte, error) {
	str := ""
	switch *s {
	case ServiceBillTypeInstall:
		str = "INSTALL"
	case ServiceBillTypeFix:
		str = "FIX"
	}
	return json.Marshal(str)
}

func (s *ServiceBillType) UnmarshalJSON(data []byte) error {
	var str string
	if err := json.Unmarshal(data, &str); err == nil {
		switch str {
		case "INSTALL":
			*s = ServiceBillTypeInstall
		case "FIX":
			*s = ServiceBillTypeFix
		default:
			return errs.NewBizError("不支持的单据状态")
		}
	}
	return nil
}

type ServiceBill struct {
	audit.Entity
	database.BaseEntity
	Number              string `gorm:"uniqueIndex"`
	Type                ServiceBillType
	State               ServiceBillState
	ProductCompanyID    *uint                 `gorm:"index"`
	ProductCompany      *companyModel.Company `gorm:"foreignKey:ProductCompanyID"`
	ProjectName         string
	ProjectAddress      string
	ProjectContact      string
	ProjectContactPhone string
	// 现场联系人
	OnSiteContact string
	OnSitePhone   string
	// 电梯信息
	ElevatorInfo string
	TotalAmount  float64
	// 下单日期
	OrderDate *time.Time
	// 处理完成日期
	ProcessedDate *time.Time
	// 回款完成日期
	FinishedDate *time.Time
	Remark       string
	Details      []ServiceBillDetail `gorm:"foreignKey:ServiceBillID"`
	Version      optimisticlock.Version
}

type ServiceBillDetail struct {
	database.BaseEntity
	ServiceBillID int `gorm:"index"`
	// 设备信息
	Device   string
	Quantity float64
	// 单价
	UnitPrice float64
	Subtotal  float64
	Remark    string
}

type ServiceBillDTO struct {
	ID                  uint                     `json:"id"`
	Number              string                   `json:"number"`
	Type                ServiceBillType          `json:"type"`
	State               ServiceBillState         `json:"state"`
	ProductCompany      *companyModel.CompanyDTO `json:"productCompany"`
	ProjectName         string                   `json:"projectName"`
	ProjectAddress      string                   `json:"projectAddress"`
	ProjectContact      string                   `json:"projectContact"`
	ProjectContactPhone string                   `json:"projectPhone"`
	OnSiteContact       string                   `json:"onSiteContact"`
	OnSitePhone         string                   `json:"onSitePhone"`
	ElevatorInfo        string                   `json:"elevatorInfo"`
	TotalAmount         float64                  `json:"totalAmount"`
	OrderDate           *time.Time               `json:"orderDate"`
	ProcessedDate       *time.Time               `json:"processedDate"`
	FinishedDate        *time.Time               `json:"finishedDate"`
	Remark              string                   `json:"remark"`
	Details             []ServiceBillDetailDTO   `json:"details"`
	Attachments         []model.AttachmentDTO    `json:"attachments"`
}

type ServiceBillDetailDTO struct {
	ID        uint    `json:"id"`
	Device    string  `json:"device"`
	Quantity  float64 `json:"quantity"`
	UnitPrice float64 `json:"unitPrice"`
	Subtotal  float64 `json:"subtotal"`
	Remark    string  `json:"remark"`
}

func (s *ServiceBill) ToDTO(attaches *[]model.AttachmentDTO) *ServiceBillDTO {
	details := make([]ServiceBillDetailDTO, len(s.Details))
	for i, d := range s.Details {
		details[i] = *d.ToDTO()
	}

	dto := s.ToBaseDTO()
	dto.Details = details
	dto.Attachments = *attaches
	return dto
}

func (s *ServiceBill) ToBaseDTO() *ServiceBillDTO {
	var productCompanyDTO *companyModel.CompanyDTO
	if s.ProductCompany != nil {
		productCompanyDTO = s.ProductCompany.ToDTO()
	}
	return &ServiceBillDTO{
		ID:                  s.ID,
		Number:              s.Number,
		Type:                s.Type,
		State:               s.State,
		ProductCompany:      productCompanyDTO,
		ProjectName:         s.ProjectName,
		ProjectAddress:      s.ProjectAddress,
		ProjectContact:      s.ProjectContact,
		ProjectContactPhone: s.ProjectContactPhone,
		OnSiteContact:       s.OnSiteContact,
		OnSitePhone:         s.OnSitePhone,
		ElevatorInfo:        s.ElevatorInfo,
		TotalAmount:         s.TotalAmount,
		OrderDate:           s.OrderDate,
		ProcessedDate:       s.ProcessedDate,
		FinishedDate:        s.FinishedDate,
		Remark:              s.Remark,
	}
}

func (s *ServiceBillDTO) ToEntity() *ServiceBill {
	details := make([]ServiceBillDetail, len(s.Details))
	for i, d := range s.Details {
		details[i] = *d.ToEntity()
	}
	entity := ServiceBill{
		BaseEntity: database.BaseEntity{
			ID: s.ID,
		},
		Number:              s.Number,
		Type:                s.Type,
		State:               s.State,
		ProjectName:         s.ProjectName,
		ProjectAddress:      s.ProjectAddress,
		ProjectContact:      s.ProjectContact,
		ProjectContactPhone: s.ProjectContactPhone,
		OnSiteContact:       s.OnSiteContact,
		OnSitePhone:         s.OnSitePhone,
		ElevatorInfo:        s.ElevatorInfo,
		TotalAmount:         s.TotalAmount,
		OrderDate:           s.OrderDate,
		ProcessedDate:       s.ProcessedDate,
		FinishedDate:        s.FinishedDate,
		Remark:              s.Remark,
		Details:             details,
	}
	if s.ProductCompany != nil && s.ProductCompany.ID != 0 {
		entity.ProductCompanyID = &(s.ProductCompany.ID)
	}
	return &entity
}

func ToBaseDTOs(entities *[]ServiceBill) *[]ServiceBillDTO {
	res := make([]ServiceBillDTO, len(*entities))
	for i, e := range *entities {
		res[i] = *e.ToBaseDTO()
	}
	return &res
}

func (s *ServiceBillDetail) ToDTO() *ServiceBillDetailDTO {
	return &ServiceBillDetailDTO{
		ID:        s.ID,
		Device:    s.Device,
		Quantity:  s.Quantity,
		UnitPrice: s.UnitPrice,
		Subtotal:  s.Subtotal,
		Remark:    s.Remark,
	}
}

func (s *ServiceBillDetailDTO) ToEntity() *ServiceBillDetail {
	return &ServiceBillDetail{
		BaseEntity: database.BaseEntity{
			ID: s.ID,
		},
		Device:    s.Device,
		Quantity:  s.Quantity,
		UnitPrice: s.UnitPrice,
		Subtotal:  s.Subtotal,
		Remark:    s.Remark,
	}
}
