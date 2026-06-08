package user

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"errors"

	"gorm.io/gorm"
)

// Repository 用户仓库
type Repository struct {
	*database.BaseRepository[User]
}

// NewUserRepository 创建用户仓库实例
func NewUserRepository(db *gorm.DB) *Repository {
	return &Repository{
		database.NewBaseRepository[User](db),
	}
}

// FindByUsername 根据用户名查找用户
func (r *Repository) FindByUsername(ctx context.Context, username string) (*User, error) {
	var user User
	res := r.GetDB(ctx).Where("username = ?", username).Take(&user)
	if res.Error != nil {
		if errors.Is(res.Error, gorm.ErrRecordNotFound) {
			return nil, errs.NewBizError("用户不存在")
		}
		return nil, errs.Wrap(res.Error)
	}
	return &user, nil
}

// FindAllEnabled 查找所有启用的用户
func (r *Repository) FindAllEnabled(ctx context.Context, pageable *result.QueryParam) (*result.PageResult[User], error) {
	return r.FindAllWithPage(ctx, pageable, "disabled = ?", false)
}

// ExistsByUsername 检查用户名是否存在
func (r *Repository) ExistsByUsername(ctx context.Context, username string) (bool, error) {
	var count int64
	err := r.GetDB(ctx).Model(&User{}).Where("username = ?", username).Count(&count).Error
	return count > 0, errs.Wrap(err)
}
