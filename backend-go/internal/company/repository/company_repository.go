package repository

import (
	"backend-go/internal/common/database"
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"backend-go/internal/company/model"
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
func (r *CompanyRepository) FindAllEnabled(ctx context.Context, pageable *result.QueryParam) (*result.PageResult[model.Company], errs.StackError) {
	return r.FindAllWithPage(ctx, pageable, "is_disabled = ?", false)
}

// FindEnabledByNameContains 根据名称查找启用的公司
func (r *CompanyRepository) FindEnabledByNameContains(ctx context.Context, name string) (*[]model.Company, errs.StackError) {
	return r.FindAll(ctx, "is_disabled = ? and name LIKE ?", false, "%"+name+"%")
}
