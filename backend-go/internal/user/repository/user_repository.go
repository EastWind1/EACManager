package repository

import (
	"backend-go/internal/common/database"
	"backend-go/internal/common/result"
	"backend-go/internal/user/model"
	"context"

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
func (r *UserRepository) FindByUsername(ctx context.Context, username string) (*model.User, error) {
	user, err := gorm.G[model.User](r.Db).Where("username = ?", username).First(ctx)
	return &user, err
}

// FindAllEnabled 查找所有启用的用户
func (r *UserRepository) FindAllEnabled(ctx context.Context, pageable *result.QueryParam) (*result.PageResult[model.User], error) {
	return r.FindAllWithPage(ctx, pageable, "is_enabled = ?", true)
}

// ExistsByUsername 检查用户名是否存在
func (r *UserRepository) ExistsByUsername(username string) (bool, error) {
	var count int64
	err := r.Db.Model(&model.User{}).Where("username = ?", username).Count(&count).Error
	return count > 0, err
}

// WithTransaction 开启事务，内部操作数据库务必使用回调传入的实例
func (r *UserRepository) WithTransaction(fn func(r *UserRepository) error) error {
	return r.Db.Transaction(func(tx *gorm.DB) error {
		return fn(NewUserRepository(tx))
	})
}
