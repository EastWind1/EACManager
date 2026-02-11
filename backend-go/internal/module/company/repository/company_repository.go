package repository

import (
	"backend-go/internal/module/company/model"
	"backend-go/internal/pkg/database"
	"backend-go/internal/pkg/result"
	"context"

	"gorm.io/gorm"
)

type CompanyRepository struct {
	*database.BaseRepository[model.Company]
}

func NewCompanyRepository(db *gorm.DB) *CompanyRepository {
	return &CompanyRepository{
		BaseRepository: database.NewBaseRepository[model.Company](db),
	}
}

// FindAllEnabled 查找所有启用的公司
func (r *CompanyRepository) FindAllEnabled(ctx context.Context, pageable *result.QueryParam) (*result.PageResult[model.Company], error) {
	return r.FindAllWithPage(ctx, pageable, "disabled = ?", false)
}

// FindEnabledByNameContains 根据名称查找启用的公司
func (r *CompanyRepository) FindEnabledByNameContains(ctx context.Context, name string) ([]model.Company, error) {
	return r.FindAll(ctx, "disabled = ? and name LIKE ?", false, "%"+name+"%")
}
