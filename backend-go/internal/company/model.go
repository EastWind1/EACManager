package company

import (
	"backend-go/internal/security"
)

type Company struct {
	ID   uint `gorm:"primaryKey"`
	Name string

	ContactName string

	ContactPhone string
	Email        string
	Address      string
	Disabled     bool
	security.Audit
}

type DTO struct {
	ID           uint   `json:"id"`
	Name         string `json:"name" validate:"required,notblank,max=100" label:"公司名称"`
	ContactName  string `json:"contactName" validate:"max=64" label:"联系人"`
	ContactPhone string `json:"contactPhone" validate:"omitempty,phone" label:"联系电话"`
	Email        string `json:"email" validate:"omitempty,email,max=128" label:"邮箱"`
	Address      string `json:"address" validate:"max=255" label:"地址"`
	Disabled     bool   `json:"disabled"`
}

func (c *Company) ToDTO() *DTO {
	return &DTO{
		ID:           c.ID,
		Name:         c.Name,
		ContactName:  c.ContactName,
		ContactPhone: c.ContactPhone,
		Email:        c.Email,
		Address:      c.Address,
		Disabled:     c.Disabled,
	}
}

func (c *DTO) ToEntity() *Company {
	return &Company{
		ID:           c.ID,
		Name:         c.Name,
		ContactName:  c.ContactName,
		ContactPhone: c.ContactPhone,
		Email:        c.Email,
		Address:      c.Address,
		Disabled:     c.Disabled,
	}
}

func ToDTOs(companies []Company) []DTO {
	if companies == nil {
		return nil
	}
	companyDTOs := make([]DTO, len(companies))
	for i, company := range companies {
		companyDTOs[i] = *company.ToDTO()
	}
	return companyDTOs
}
