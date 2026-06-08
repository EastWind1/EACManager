package company

import (
	"backend-go/pkg/database"
	"backend-go/pkg/result"
	"context"

	"gorm.io/gorm"
)

type Repository struct {
	*database.BaseRepository[Company]
}

func NewCompanyRepository(db *gorm.DB) *Repository {
	return &Repository{
		BaseRepository: database.NewBaseRepository[Company](db),
	}
}

// FindAllEnabled 查找所有启用的公司
func (r *Repository) FindAllEnabled(ctx context.Context, pageable *result.QueryParam) (*result.PageResult[Company], error) {
	return r.FindAllWithPage(ctx, pageable, "disabled = ?", false)
}

// FindEnabledByNameContains 根据名称查找启用的公司
func (r *Repository) FindEnabledByNameContains(ctx context.Context, name string) ([]Company, error) {
	return r.FindAll(ctx, "disabled = ? and name LIKE ?", false, "%"+name+"%")
}
