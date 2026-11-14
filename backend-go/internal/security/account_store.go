package security

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"errors"

	"gorm.io/gorm"
)

type AccountRepository struct {
	*database.BaseRepository[Account]
}

func newAccountRepository(db *gorm.DB) *AccountRepository {
	return &AccountRepository{BaseRepository: database.NewBaseRepository[Account](db)}
}

func (r *AccountRepository) FindByUsername(ctx context.Context, username string) (*Account, error) {
	account, err := gorm.G[Account](r.Ctx(ctx)).Where("username = ?", username).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &account, nil
}

func (r *AccountRepository) FindEnabledByUsername(ctx context.Context, username string) (*Account, error) {
	account, err := gorm.G[Account](r.Ctx(ctx)).Where("username = ? AND disabled = ?", username, false).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &account, nil
}

func (r *AccountRepository) FindByID(ctx context.Context, id uint) (*Account, error) {
	record, err := gorm.G[Account](r.Ctx(ctx)).Where("id = ?", id).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &record, nil
}

func (r *AccountRepository) FindByIDs(ctx context.Context, ids []uint) ([]Account, error) {
	if len(ids) == 0 {
		return []Account{}, nil
	}
	accounts, err := gorm.G[Account](r.Ctx(ctx)).Where("id IN ?", ids).Order("id").Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return accounts, nil
}

func (r *AccountRepository) FindEnabledList(ctx context.Context) ([]Account, error) {
	accounts, err := gorm.G[Account](r.Ctx(ctx)).Where("disabled = ?", false).Order("id").Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return accounts, nil
}

func (r *AccountRepository) FindEnabledPage(ctx context.Context, pageable *result.QueryParam) (*result.PageResult[Account], error) {
	return r.Page(ctx, r.Chain(ctx).Where("disabled = ?", false), pageable)
}

func (r *AccountRepository) Create(ctx context.Context, account *Account) error {
	if err := gorm.G[Account](r.Ctx(ctx)).Create(ctx, account); err != nil {
		if database.IsDuplicateKey(err) {
			return errs.NewBizError("用户名已存在")
		}
		return errs.Wrap(err)
	}
	return nil
}

func (r *AccountRepository) UpdatePassword(ctx context.Context, account *Account) error {
	affected, err := gorm.G[*Account](r.Ctx(ctx)).Select("password").Where("id = ?", account.ID).Updates(ctx, account)
	if err != nil {
		return errs.Wrap(err)
	}
	if affected == 0 {
		return errs.NewBizError("用户不存在")
	}
	return nil
}

func (r *AccountRepository) Disable(ctx context.Context, username string) (bool, error) {
	affected, err := gorm.G[*Account](r.Ctx(ctx)).Select("disabled").
		Where("username = ?", username).Updates(ctx, &Account{Disabled: true})
	if err != nil {
		return false, errs.Wrap(err)
	}
	return affected > 0, nil
}
