package model

import (
	"backend-go/internal/attach/model"
	"backend-go/internal/common/audit"
	"backend-go/internal/common/result"
	companyModel "backend-go/internal/company/model"
)

type ServiceBillState string

const (
	ServiceBillStateCreated    ServiceBillState = "CREATED"
	ServiceBillStateProcessing ServiceBillState = "PROCESSING"
	ServiceBillStateProcessed  ServiceBillState = "PROCESSED"
	ServiceBillStateFinished   ServiceBillState = "FINISHED"
)

type ServiceBillType string

const (
	ServiceBillTypeInstall ServiceBillType = "INSTALL"
	ServiceBillTypeFix     ServiceBillType = "FIX"
)

type ServiceBill struct {
	audit.Entity
	ID               int                   `json:"id" gorm:"primaryKey"`
	Number           string                `json:"number" gorm:"uniqueIndex;size:100"`
	Type             ServiceBillType       `json:"type" gorm:"size:50"`
	State            ServiceBillState      `json:"state" gorm:"size:50"`
	ProductCompanyID *int                  `json:"productCompanyId" gorm:"index"`
	ProductCompany   *companyModel.Company `json:"productCompany" gorm:"foreignKey:ProductCompanyID"`
	ProjectName      string                `json:"projectName" gorm:"size:200"`
	ProjectAddress   string                `json:"projectAddress" gorm:"size:500"`
	ProjectContact   string                `json:"projectContact" gorm:"size:100"`
	ProjectPhone     string                `json:"projectPhone" gorm:"size:50"`
	OnSiteContact    string                `json:"onSiteContact" gorm:"size:100"`
	OnSitePhone      string                `json:"onSitePhone" gorm:"size:50"`
	ElevatorInfo     string                `json:"elevatorInfo" gorm:"size:500"`
	TotalAmount      float64               `json:"totalAmount"`
	OrderDate        *string               `json:"orderDate"`
	ProcessedDate    *string               `json:"processedDate"`
	FinishedDate     *string               `json:"finishedDate"`
	Remark           string                `json:"remark" gorm:"size:1000"`
	Details          []ServiceBillDetail   `json:"details" gorm:"foreignKey:ServiceBillID"`
	Version          int                   `json:"version" gorm:"default:0"`
}

type ServiceBillDetail struct {
	ID            int     `json:"id" gorm:"primaryKey"`
	ServiceBillID int     `json:"serviceBillId" gorm:"index"`
	Device        string  `json:"device" gorm:"size:200"`
	Quantity      float64 `json:"quantity"`
	UnitPrice     float64 `json:"unitPrice"`
	Subtotal      float64 `json:"subtotal"`
	Remark        string  `json:"remark" gorm:"size:500"`
}

type ServiceBillDTO struct {
	ID             int                      `json:"id"`
	Number         string                   `json:"number"`
	Type           ServiceBillType          `json:"type"`
	State          ServiceBillState         `json:"state"`
	ProductCompany *companyModel.CompanyDTO `json:"productCompany"`
	ProjectName    string                   `json:"projectName"`
	ProjectAddress string                   `json:"projectAddress"`
	ProjectContact string                   `json:"projectContact"`
	ProjectPhone   string                   `json:"projectPhone"`
	OnSiteContact  string                   `json:"onSiteContact"`
	OnSitePhone    string                   `json:"onSitePhone"`
	ElevatorInfo   string                   `json:"elevatorInfo"`
	TotalAmount    float64                  `json:"totalAmount"`
	OrderDate      *string                  `json:"orderDate"`
	ProcessedDate  *string                  `json:"processedDate"`
	FinishedDate   *string                  `json:"finishedDate"`
	Remark         string                   `json:"remark"`
	Details        []ServiceBillDetailDTO   `json:"details"`
	Attachments    []model.AttachmentDTO    `json:"attachments"`
}

type ServiceBillDetailDTO struct {
	ID        int     `json:"id"`
	Device    string  `json:"device"`
	Quantity  float64 `json:"quantity"`
	UnitPrice float64 `json:"unitPrice"`
	Subtotal  float64 `json:"subtotal"`
	Remark    string  `json:"remark"`
}

type ServiceBillQueryParam struct {
	Number             string             `form:"number"`
	States             []ServiceBillState `form:"states"`
	ProjectName        string             `form:"projectName"`
	OrderStartDate     *string            `form:"orderStartDate"`
	OrderEndDate       *string            `form:"orderEndDate"`
	ProcessedStartDate *string            `form:"processedStartDate"`
	ProcessedEndDate   *string            `form:"processedEndDate"`
	result.QueryParam
	result.SortParam
}

type MonthSumAmount struct {
	Month  string  `json:"month" gorm:"primaryKey"`
	Amount float64 `json:"amount"`
}

func (s *ServiceBill) ToDTO() ServiceBillDTO {
	details := make([]ServiceBillDetailDTO, len(s.Details))
	for i, d := range s.Details {
		details[i] = d.ToDTO()
	}

	var productCompanyDTO *companyModel.CompanyDTO
	if s.ProductCompany != nil {
		dto := s.ProductCompany.ToDTO()
		productCompanyDTO = &dto
	}

	return ServiceBillDTO{
		ID:             s.ID,
		Number:         s.Number,
		Type:           s.Type,
		State:          s.State,
		ProductCompany: productCompanyDTO,
		ProjectName:    s.ProjectName,
		ProjectAddress: s.ProjectAddress,
		ProjectContact: s.ProjectContact,
		ProjectPhone:   s.ProjectPhone,
		OnSiteContact:  s.OnSiteContact,
		OnSitePhone:    s.OnSitePhone,
		ElevatorInfo:   s.ElevatorInfo,
		TotalAmount:    s.TotalAmount,
		OrderDate:      s.OrderDate,
		ProcessedDate:  s.ProcessedDate,
		FinishedDate:   s.FinishedDate,
		Remark:         s.Remark,
		Details:        details,
	}
}

func (s *ServiceBill) ToBaseDTO() ServiceBillDTO {
	details := make([]ServiceBillDetailDTO, len(s.Details))
	for i, d := range s.Details {
		details[i] = d.ToDTO()
	}

	return ServiceBillDTO{
		ID:             s.ID,
		Number:         s.Number,
		Type:           s.Type,
		State:          s.State,
		ProjectName:    s.ProjectName,
		ProjectAddress: s.ProjectAddress,
		TotalAmount:    s.TotalAmount,
		OrderDate:      s.OrderDate,
		ProcessedDate:  s.ProcessedDate,
		FinishedDate:   s.FinishedDate,
		Details:        details,
	}
}

func (s *ServiceBillDetail) ToDTO() ServiceBillDetailDTO {
	return ServiceBillDetailDTO{
		ID:        s.ID,
		Device:    s.Device,
		Quantity:  s.Quantity,
		UnitPrice: s.UnitPrice,
		Subtotal:  s.Subtotal,
		Remark:    s.Remark,
	}
}
