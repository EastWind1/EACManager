package attach

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"context"
	"time"

	"gorm.io/gorm"
)

type Repository struct {
	*database.BaseRepository[Attach]
}

func NewRepository(db *gorm.DB) *Repository {
	return &Repository{
		BaseRepository: database.NewBaseRepository[Attach](db),
	}
}

// PersistByIds 把瞬时态附件一次性置为持久态，返回真正被更新的行数
//
// 单条条件更新，不做一次 select：
//   - 条件里的 status 让「已提交」的附件无法二次提交；
//   - 受影响行数让调用方知道哪些 id 不存在或已被提交。
func (r *Repository) PersistByIds(ctx context.Context, ids []uint) (int64, error) {
	if len(ids) == 0 {
		return 0, nil
	}
	affected, err := gorm.G[*Attach](r.Ctx(ctx)).Select("status").
		Where("id IN ? AND status = ?", ids, StatusTransient).
		Updates(ctx, &Attach{Status: StatusPersistent})
	if err != nil {
		return 0, errs.Wrap(err)
	}
	return int64(affected), nil
}

func (r *Repository) DeleteByIDs(ctx context.Context, ids []uint) error {
	if len(ids) == 0 {
		return nil
	}
	_, err := gorm.G[Attach](r.Ctx(ctx)).Where("id IN ?", ids).Delete(ctx)
	return errs.Wrap(err)
}

func (r *Repository) FindByStatusBefore(ctx context.Context, status Status, cutoff time.Time) ([]Attach, error) {
	attachments, err := gorm.G[Attach](r.Ctx(ctx)).
		Where("status = ? AND created_date < ?", status, cutoff).Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return attachments, nil
}
