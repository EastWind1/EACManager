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
	Name         string `json:"name"`
	ContactName  string `json:"contactName"`
	ContactPhone string `json:"contactPhone"`
	Email        string `json:"email"`
	Address      string `json:"address"`
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
