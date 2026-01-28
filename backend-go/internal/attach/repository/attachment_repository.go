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
func (r *AttachmentRepository) FindByBill(ctx context.Context, billID int, billType model.BillType) (*[]model.Attachment, error) {
	var attachments []model.Attachment
	err := r.Db.WithContext(ctx).Table("attachment").
		Joins("right join bill_attach_relation on attachment.id = bill_attach_relation.billId").
		Select("attachment.*").
		Where("bill_attach_relation.bill_id = ? and bill_attach_relation.bill_type = ?", billID, billType).
		Find(&attachments).Error
	if err != nil {
		return nil, err
	}
	return &attachments, nil
}

// FindByBillIsNull 查找未关联业务单据的附件
func (r *AttachmentRepository) FindByBillIsNull(ctx context.Context) (*[]model.Attachment, error) {
	var attachments []model.Attachment
	err := r.Db.WithContext(ctx).Table("attachment").
		Where("not exists (select attach_id from bill_attach_relation where attachment.attach_id = attachment.id)").
		Find(&attachments).Error
	if err != nil {
		return nil, err
	}
	return &attachments, nil
}

// WithTransaction 开启事务，内部操作数据库务必使用回调传入的实例
func (r *AttachmentRepository) WithTransaction(fn func(r *AttachmentRepository) error) error {
	return r.Db.Transaction(func(tx *gorm.DB) error {
		return fn(NewAttachmentRepository(tx))
	})
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
func (r *BillAttachRelationRepository) FindByBillIDAndBillType(ctx context.Context, billID int, billType model.BillType) (*[]model.BillAttachRelation, error) {
	return r.FindAll(ctx, "bill_id =? and bill_type =?", billID, billType)
}
