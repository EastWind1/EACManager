package attach

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"context"
	"time"

	"gorm.io/gorm"
)

// Repository 附件仓库
type Repository struct {
	*database.BaseRepository[Attachment]
}

func NewRepository(db *gorm.DB) *Repository {
	return &Repository{
		BaseRepository: database.NewBaseRepository[Attachment](db),
	}
}

// PersistByIds 持久化附件
func (r *Repository) PersistByIds(ctx context.Context, ids []uint) (int64, error) {
	if len(ids) == 0 {
		return 0, nil
	}
	var t Attachment
	res := r.GetDB(ctx).
		Model(&t).
		Where("id in ? and status = ?", ids, StatusTransient).
		Update("status", StatusPersistent)
	if res.Error != nil {
		return 0, errs.Wrap(res.Error)
	}
	return res.RowsAffected, nil
}

// DeleteByIDs 根据 ID 批量删除附件
func (r *Repository) DeleteByIDs(ctx context.Context, ids []uint) error {
	if len(ids) == 0 {
		return nil
	}
	var t Attachment
	return errs.Wrap(r.GetDB(ctx).Where("id in ?", ids).Delete(&t).Error)
}

// FindByStatusBefore 查找某状态下创建时间早于 cutoff 的附件
func (r *Repository) FindByStatusBefore(ctx context.Context, status Status, cutoff time.Time) ([]Attachment, error) {
	attachments := make([]Attachment, 0)
	err := r.GetDB(ctx).
		Where("status = ? and created_date < ?", status, cutoff).
		Find(&attachments).Error
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return attachments, nil
}
