package attach

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"context"

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

// FindByBill 根据业务单据查找附件
func (r *Repository) FindByBill(ctx context.Context, billID uint, billType BillType) ([]Attachment, error) {
	var attachments []Attachment
	err := r.GetDB(ctx).WithContext(ctx).Table("attachment").
		Joins("right join bill_attach_relation on attachment.id = bill_attach_relation.attach_id").
		Select("attachment.*").
		Where("bill_attach_relation.bill_id = ? and bill_attach_relation.bill_type = ?", billID, billType).
		Find(&attachments).Error
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return attachments, nil
}

// BillAttachRelRepo 业务单据附件关系仓库
type BillAttachRelRepo struct {
	*database.BaseRepository[BillAttachRelation]
}

func NewBillAttachRelRepo(db *gorm.DB) *BillAttachRelRepo {
	return &BillAttachRelRepo{
		BaseRepository: database.NewBaseRepository[BillAttachRelation](db),
	}
}

// FindByBillIDAndBillType 根据业务单据ID和类型查找附件关系
func (r *BillAttachRelRepo) FindByBillIDAndBillType(ctx context.Context, billID uint, billType BillType) ([]BillAttachRelation, error) {
	res := make([]BillAttachRelation, 0)
	if err := r.GetDB(ctx).WithContext(ctx).Preload("Attach").
		Find(&res, "bill_attach_relation.bill_id = ? and bill_attach_relation.bill_type = ?", billID, billType).Error; err != nil {
		return nil, errs.Wrap(err)
	}
	return res, nil
}
