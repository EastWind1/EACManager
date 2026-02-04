package model

import (
	"backend-go/internal/common/audit"
	"backend-go/internal/common/database"
)

// Company 公司
type Company struct {
	database.BaseEntity
	Name string
	// ContactName 联系人名称
	ContactName string
	// ContactPhone 联系人电话
	ContactPhone string
	Email        string
	Address      string
	IsDisabled   bool `gorm:"defalut:false"`
	audit.Entity
}

// CompanyDTO 公司 DTO
type CompanyDTO struct {
	ID           uint   `json:"id"`
	Name         string `json:"name"`
	ContactName  string `json:"contactName"`
	ContactPhone string `json:"contactPhone"`
	Email        string `json:"email"`
	Address      string `json:"address"`
	IsDisabled   bool   `json:"isDisabled"`
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
		IsDisabled:   c.IsDisabled,
	}
}

// ToEntity 创建公司实体
func (c *CompanyDTO) ToEntity() *Company {
	return &Company{
		BaseEntity: database.BaseEntity{
			ID: c.ID,
		},
		Name:         c.Name,
		ContactName:  c.ContactName,
		ContactPhone: c.ContactPhone,
		Email:        c.Email,
		Address:      c.Address,
		IsDisabled:   c.IsDisabled,
	}
}

// ToDTOs 创建公司 DTO 列表
func ToDTOs(companies *[]Company) *[]CompanyDTO {
	if companies == nil {
		return nil
	}
	companyDTOs := make([]CompanyDTO, len(*companies))
	for i, company := range *companies {
		companyDTOs[i] = *company.ToDTO()
	}
	return &companyDTOs
}
