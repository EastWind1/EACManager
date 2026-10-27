package bill

import (
	"backend-go/internal/attach"
	"backend-go/internal/company"
	"backend-go/internal/security"
	"backend-go/pkg/errs"
	"encoding/json"
	"time"

	"gorm.io/plugin/optimisticlock"
)

type State int

const (
	Created State = iota
	Processing
	Processed
	Finished
)

func (s *State) String() string {
	return []string{"CREATED", "PROCESSING", "PROCESSED", "FINISHED"}[*s]
}

func (s *State) MarshalJSON() ([]byte, error) {
	str := ""
	switch *s {
	case Created:
		str = "CREATED"
	case Processing:
		str = "PROCESSING"
	case Processed:
		str = "PROCESSED"
	case Finished:
		str = "FINISHED"

	}
	return json.Marshal(str)
}

func (s *State) UnmarshalJSON(data []byte) error {
	var str string
	if err := json.Unmarshal(data, &str); err == nil {
		switch str {
		case "CREATED":
			*s = Created
		case "PROCESSING":
			*s = Processing
		case "PROCESSED":
			*s = Processed
		case "FINISHED":
			*s = Finished
		default:
			return errs.NewBizError("不支持的单据状态")
		}
	}
	return nil
}

type Type int

const (
	Install Type = iota
	Fix
)

func (s *Type) MarshalJSON() ([]byte, error) {
	str := ""
	switch *s {
	case Install:
		str = "INSTALL"
	case Fix:
		str = "FIX"
	}
	return json.Marshal(str)
}

func (s *Type) UnmarshalJSON(data []byte) error {
	var str string
	if err := json.Unmarshal(data, &str); err == nil {
		switch str {
		case "INSTALL":
			*s = Install
		case "FIX":
			*s = Fix
		default:
			return errs.NewBizError("不支持的单据状态")
		}
	}
	return nil
}

type ServiceBill struct {
	security.Audit
	ID                  uint   `gorm:"primaryKey"`
	Number              string `gorm:"uniqueIndex"`
	Type                Type
	State               State
	ProductCompanyID    *uint            `gorm:"index"`
	ProductCompany      *company.Company `gorm:"foreignKey:ProductCompanyID"`
	ProjectName         string
	ProjectAddress      string
	ProjectContact      string
	ProjectContactPhone string
	OnSiteContact       string
	OnSitePhone         string
	ElevatorInfo        string
	TotalAmount         float64
	OrderDate           *time.Time
	ProcessedDate       *time.Time
	FinishedDate        *time.Time
	Remark              string
	Details             []Detail `gorm:"foreignKey:ServiceBillID"`
	Version             optimisticlock.Version
}

type Detail struct {
	ID            uint `gorm:"primaryKey"`
	ServiceBillID int  `gorm:"index"`

	Device   string
	Quantity float64

	UnitPrice float64
	Subtotal  float64
	Remark    string
}

func (d *Detail) TableName() string {
	return "service_bill_detail"
}

type DTO struct {
	ID                  uint         `json:"id"`
	Number              string       `json:"number"`
	Type                Type         `json:"type"`
	State               State        `json:"state"`
	ProductCompany      *company.DTO `json:"productCompany"`
	ProjectName         string       `json:"projectName"`
	ProjectAddress      string       `json:"projectAddress"`
	ProjectContact      string       `json:"projectContact"`
	ProjectContactPhone string       `json:"projectPhone"`
	OnSiteContact       string       `json:"onSiteContact"`
	OnSitePhone         string       `json:"onSitePhone"`
	ElevatorInfo        string       `json:"elevatorInfo"`
	TotalAmount         float64      `json:"totalAmount"`
	OrderDate           *time.Time   `json:"orderDate"`
	ProcessedDate       *time.Time   `json:"processedDate"`
	FinishedDate        *time.Time   `json:"finishedDate"`
	Remark              string       `json:"remark"`
	Details             []DetailDTO  `json:"details"`
	Attachments         []attach.DTO `json:"attachments"`
}

type DetailDTO struct {
	ID        uint    `json:"id"`
	Device    string  `json:"device"`
	Quantity  float64 `json:"quantity"`
	UnitPrice float64 `json:"unitPrice"`
	Subtotal  float64 `json:"subtotal"`
	Remark    string  `json:"remark"`
}

func (s *ServiceBill) ToDTO(attaches []attach.Attach) *DTO {
	details := make([]DetailDTO, len(s.Details))
	for i, d := range s.Details {
		details[i] = *d.ToDTO()
	}
	attachDTOes := make([]attach.DTO, len(attaches))
	for i, d := range attaches {
		attachDTOes[i] = *d.ToDTO()
	}
	dto := s.ToBaseDTO()
	dto.Details = details
	dto.Attachments = attachDTOes
	return dto
}

func (s *ServiceBill) ToBaseDTO() *DTO {
	var productCompanyDTO *company.DTO
	if s.ProductCompany != nil {
		productCompanyDTO = s.ProductCompany.ToDTO()
	}
	return &DTO{
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

func (s *DTO) ToEntity() *ServiceBill {
	details := make([]Detail, len(s.Details))
	for i, d := range s.Details {
		details[i] = *d.ToEntity()
	}
	entity := ServiceBill{
		ID:                  s.ID,
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

func ToBaseDTOs(entities []ServiceBill) []DTO {
	res := make([]DTO, len(entities))
	for i, e := range entities {
		res[i] = *e.ToBaseDTO()
	}
	return res
}

func (s *Detail) ToDTO() *DetailDTO {
	return &DetailDTO{
		ID:        s.ID,
		Device:    s.Device,
		Quantity:  s.Quantity,
		UnitPrice: s.UnitPrice,
		Subtotal:  s.Subtotal,
		Remark:    s.Remark,
	}
}

func (s *DetailDTO) ToEntity() *Detail {
	return &Detail{
		ID:        s.ID,
		Device:    s.Device,
		Quantity:  s.Quantity,
		UnitPrice: s.UnitPrice,
		Subtotal:  s.Subtotal,
		Remark:    s.Remark,
	}
}
