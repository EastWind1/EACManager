package bill

import (
	"backend-go/internal/attach"
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"context"

	"gorm.io/gorm"
)

// AttachNamespace 服务单附件的命名空间
//
// 只用于隔离附件存储，附件模块不解释其含义。
const AttachNamespace = "SERVICE_BILL"

// ServiceBillAttachRelation 服务单附件关联
type ServiceBillAttachRelation struct {
	ID            uint `gorm:"primaryKey"`
	ServiceBillId uint `gorm:"index"`
	AttachId      uint
	Attach        attach.Attachment `gorm:"foreignKey:AttachId"`
}

// ServiceBillAttachRelRepo 服务单附件关联仓库
type ServiceBillAttachRelRepo struct {
	*database.BaseRepository[ServiceBillAttachRelation]
}

func NewServiceBillAttachRelRepo(db *gorm.DB) *ServiceBillAttachRelRepo {
	return &ServiceBillAttachRelRepo{
		BaseRepository: database.NewBaseRepository[ServiceBillAttachRelation](db),
	}
}

// FindByBillID 根据服务单 ID 查找关联记录
func (r *ServiceBillAttachRelRepo) FindByBillID(ctx context.Context, billID uint) ([]ServiceBillAttachRelation, error) {
	res := make([]ServiceBillAttachRelation, 0)
	if err := r.GetDB(ctx).WithContext(ctx).Preload("Attach").
		Find(&res, "service_bill_attach_relation.service_bill_id = ?", billID).Error; err != nil {
		return nil, errs.Wrap(err)
	}
	return res, nil
}

// DeleteByBillIDAndAttachIDs 删除服务单指定附件的关联记录
func (r *ServiceBillAttachRelRepo) DeleteByBillIDAndAttachIDs(ctx context.Context, billID uint, attachIDs []uint) error {
	if len(attachIDs) == 0 {
		return nil
	}
	var t ServiceBillAttachRelation
	return errs.Wrap(r.GetDB(ctx).WithContext(ctx).
		Where("service_bill_id = ? and attach_id in ?", billID, attachIDs).
		Delete(&t).Error)
}

// AttachRelService 服务单附件关联业务
//
// 服务单与附件的关联由本模块自行维护；附件的落库、提交与删除交给附件模块，
// 差异对比只是「目标集合」与「本单据当前关联」的集合运算。
type AttachRelService struct {
	relRepo   *ServiceBillAttachRelRepo
	attachSrv *attach.Service
}

func NewAttachRelService(db *gorm.DB, attachSrv *attach.Service) *AttachRelService {
	return &AttachRelService{
		relRepo:   NewServiceBillAttachRelRepo(db),
		attachSrv: attachSrv,
	}
}

// FindAttachments 获取服务单附件
func (s *AttachRelService) FindAttachments(ctx context.Context, billID uint) ([]attach.Attachment, error) {
	rels, err := s.relRepo.FindByBillID(ctx, billID)
	if err != nil {
		return nil, err
	}
	attaches := make([]attach.Attachment, 0, len(rels))
	for _, rel := range rels {
		attaches = append(attaches, rel.Attach)
	}
	return attaches, nil
}

// UpdateAttachments 将服务单附件更新为目标集合
func (s *AttachRelService) UpdateAttachments(ctx context.Context, billID uint, dtoes []attach.AttachmentDTO) error {
	rels, err := s.relRepo.FindByBillID(ctx, billID)
	if err != nil {
		return err
	}

	currentIDs := make(map[uint]bool, len(rels))
	for _, rel := range rels {
		currentIDs[rel.Attach.ID] = true
	}

	// 目标集合中不在当前关联里的即为新增，其余为待删除
	addedIDs := make([]uint, 0, len(dtoes))
	targetIDs := make(map[uint]bool, len(dtoes))
	for _, dto := range dtoes {
		id := uint(dto.ID)
		if id == 0 || targetIDs[id] {
			continue
		}
		targetIDs[id] = true
		if !currentIDs[id] {
			addedIDs = append(addedIDs, id)
		}
	}
	removedIDs := make([]uint, 0, len(rels))
	for _, rel := range rels {
		if !targetIDs[rel.Attach.ID] {
			removedIDs = append(removedIDs, rel.Attach.ID)
		}
	}

	// 先提交新增附件，再断开关联、删除附件与文件
	if len(addedIDs) > 0 {
		if err = s.attachSrv.Persist(ctx, addedIDs); err != nil {
			return err
		}
		for _, attachID := range addedIDs {
			rel := &ServiceBillAttachRelation{
				ServiceBillId: billID,
				AttachId:      attachID,
			}
			if err = s.relRepo.Create(ctx, rel); err != nil {
				return err
			}
		}
	}
	if len(removedIDs) > 0 {
		if err = s.relRepo.DeleteByBillIDAndAttachIDs(ctx, billID, removedIDs); err != nil {
			return err
		}
		if err = s.attachSrv.Delete(ctx, removedIDs); err != nil {
			return err
		}
	}
	return nil
}
