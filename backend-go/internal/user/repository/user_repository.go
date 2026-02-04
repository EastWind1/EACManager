package repository

import (
	"backend-go/internal/common/database"
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"backend-go/internal/user/model"
	"context"
	"errors"

	"gorm.io/gorm"
)

// UserRepository 用户仓库
type UserRepository struct {
	*database.BaseRepository[model.User]
}

// NewUserRepository 创建用户仓库实例
func NewUserRepository(db *gorm.DB) *UserRepository {
	return &UserRepository{
		database.NewBaseRepository[model.User](db),
	}
}

// FindByUsername 根据用户名查找用户
func (r *UserRepository) FindByUsername(ctx context.Context, username string) (*model.User, errs.StackError) {
	var user model.User
	res := r.GetDB(ctx).Where("username = ?", username).Take(&user)
	if res.Error != nil {
		if errors.Is(res.Error, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(res.Error)
	}
	return &user, nil
}

// FindAllEnabled 查找所有启用的用户
func (r *UserRepository) FindAllEnabled(ctx context.Context, pageable *result.QueryParam) (*result.PageResult[model.User], errs.StackError) {
	return r.FindAllWithPage(ctx, pageable, "is_enabled = ?", true)
}

// ExistsByUsername 检查用户名是否存在
func (r *UserRepository) ExistsByUsername(ctx context.Context, username string) (bool, errs.StackError) {
	var count int64
	err := r.GetDB(ctx).Model(&model.User{}).Where("username = ?", username).Count(&count).Error
	return count > 0, errs.Wrap(err)
}
