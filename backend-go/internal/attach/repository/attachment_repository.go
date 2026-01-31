package repository

import (
	"backend-go/internal/attach/model"
	"backend-go/internal/common/database"
	"context"

	"gorm.io/gorm"
)

// AttachmentRepository 附件仓库
type AttachmentRepository struct {
	*database.BaseRepository[model.Attachment]
}

func NewAttachmentRepository(db *gorm.DB) *AttachmentRepository {
	return &AttachmentRepository{
		BaseRepository: database.NewBaseRepository[model.Attachment](db),
	}
}

// FindByBill 根据业务单据查找附件
func (r *AttachmentRepository) FindByBill(ctx context.Context, billID uint, billType model.BillType) (*[]model.Attachment, error) {
	var attachments []model.Attachment
	err := r.GetDB(ctx).WithContext(ctx).Table("attachment").
		Joins("right join bill_attach_relation on attachment.id = bill_attach_relation.attach_id").
		Select("attachment.*").
		Where("bill_attach_relation.bill_id = ? and bill_attach_relation.bill_type = ?", billID, billType).
		Find(&attachments).Error
	if err != nil {
		return nil, err
	}
	return &attachments, nil
}

// BillAttachRelationRepository 业务单据附件关系仓库
type BillAttachRelationRepository struct {
	*database.BaseRepository[model.BillAttachRelation]
}

func NewBillAttachRelationRepository(db *gorm.DB) *BillAttachRelationRepository {
	return &BillAttachRelationRepository{
		BaseRepository: database.NewBaseRepository[model.BillAttachRelation](db),
	}
}

// FindByBillIDAndBillType 根据业务单据ID和类型查找附件关系
func (r *BillAttachRelationRepository) FindByBillIDAndBillType(ctx context.Context, billID uint, billType model.BillType) (*[]model.BillAttachRelation, error) {
	var res []model.BillAttachRelation
	r.GetDB(ctx).WithContext(ctx).Joins("attachment").
		Find(&res, "bill_attach_relation.bill_id = ? and bill_attach_relation.bill_type = ?", billID, billType)
	return &res, nil
}
