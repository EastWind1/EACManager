package company

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
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

func (r *Repository) FindAllEnabled(ctx context.Context, pageable *result.QueryParam) (*result.PageResult[Company], error) {
	return r.Page(ctx, r.Chain(ctx).Where("disabled = ?", false), pageable)
}

func (r *Repository) FindEnabledByNameContains(ctx context.Context, name string) ([]Company, error) {
	return r.FindAll(ctx, "disabled = ? AND name LIKE ?", false, "%"+name+"%")
}

// Update 更新公司信息：只更新表单里的列
func (r *Repository) Update(ctx context.Context, company *Company) error {
	affected, err := gorm.G[*Company](r.Ctx(ctx)).Select(
		"name", "contact_name", "contact_phone", "email", "address").
		Where("id = ?", company.ID).Updates(ctx, company)
	if err != nil {
		return errs.Wrap(err)
	}
	if affected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return nil
}

// SetDisabled 只更新禁用列
func (r *Repository) SetDisabled(ctx context.Context, id uint) error {
	affected, err := gorm.G[*Company](r.Ctx(ctx)).Select("name", "contact_name", "contact_phone", "email", "address").
		Select("disabled").
		Where("id = ?", id).
		Updates(ctx, &Company{Disabled: true})
	if err != nil {
		return errs.Wrap(err)
	}
	if affected == 0 {
		return errs.NewBizError("公司不存在")
	}
	return nil
}
