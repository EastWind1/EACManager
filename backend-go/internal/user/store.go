package user

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"context"

	"gorm.io/gorm"
)

type Repository struct {
	*database.BaseRepository[UserProfile]
}

func newUserRepository(db *gorm.DB) *Repository {
	return &Repository{BaseRepository: database.NewBaseRepository[UserProfile](db)}
}

func (r *Repository) FindByUserID(ctx context.Context, userID uint) (*UserProfile, error) {
	profile, err := gorm.G[UserProfile](r.Ctx(ctx)).Where("user_id = ?", userID).Take(ctx)
	if err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &profile, nil
}

func (r *Repository) FindByUserIDs(ctx context.Context, userIDs []uint) ([]UserProfile, error) {
	if len(userIDs) == 0 {
		return []UserProfile{}, nil
	}
	res, err := gorm.G[UserProfile](r.Ctx(ctx)).Where("user_id IN ?", userIDs).Order("user_id").Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return res, nil
}

func (r *Repository) Create(ctx context.Context, profile *UserProfile) error {
	return errs.Wrap(gorm.G[UserProfile](r.Ctx(ctx)).Create(ctx, profile))
}

func (r *Repository) Update(ctx context.Context, profile *UserProfile) error {
	affected, err := gorm.G[*UserProfile](r.Ctx(ctx)).
		Select("name", "phone", "email").
		Where("user_id = ?", profile.UserID).Updates(ctx, profile)
	if err != nil {
		return errs.Wrap(err)
	}
	if affected == 0 {
		return errs.NewBizError("用户资料不存在")
	}
	return nil
}
