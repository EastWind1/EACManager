package model

import (
	"backend-go/internal/common/audit"

	"gorm.io/gorm"
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
	Disabled     bool `gorm:"defalut:false"`
	audit.Audit
}

func (c *Company) BeforeCreate(db *gorm.DB) (err error) {
	return c.Audit.SetCreator(db)
}

func (c *Company) BeforeUpdate(db *gorm.DB) (err error) {
	return c.Audit.SetModifier(db)
}

// CompanyDTO 公司 DTO
type CompanyDTO struct {
	ID           uint   `json:"id"`
	Name         string `json:"name"`
	ContactName  string `json:"contactName"`
	ContactPhone string `json:"contactPhone"`
	Email        string `json:"email"`
	Address      string `json:"address"`
	Disabled     bool   `json:"disabled"`
}

// ToDTO 公司 DTO
func (c *Company) ToDTO() *CompanyDTO {
	return &CompanyDTO{
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
func (c *CompanyDTO) ToEntity() *Company {
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
func ToDTOs(companies []Company) []CompanyDTO {
	if companies == nil {
		return nil
	}
	companyDTOs := make([]CompanyDTO, len(companies))
	for i, company := range companies {
		companyDTOs[i] = *company.ToDTO()
	}
	return companyDTOs
}
