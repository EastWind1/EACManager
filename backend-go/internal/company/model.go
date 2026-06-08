package company

import (
	"backend-go/pkg/audit"
)

// Company 公司
type Company struct {
	ID   uint `gorm:"primaryKey"`
	Name string
	// ContactName 联系人名称
	ContactName string
	// ContactPhone 联系人电话
	ContactPhone string
	Email        string
	Address      string
	Disabled     bool
	audit.Audit
}

// DTO 公司 DTO
type DTO struct {
	ID           uint   `json:"id"`
	Name         string `json:"name"`
	ContactName  string `json:"contactName"`
	ContactPhone string `json:"contactPhone"`
	Email        string `json:"email"`
	Address      string `json:"address"`
	Disabled     bool   `json:"disabled"`
}

// ToDTO 公司 DTO
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

// ToEntity 创建公司实体
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

// ToDTOs 创建公司 DTO 列表
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
