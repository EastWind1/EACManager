package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"context"

	"gorm.io/gorm"
)

// 只用于隔离附件存储，附件模块不解释其含义。
const AttachNamespace = "REIMBURSEMENT"

type AttachRelation struct {
	ID          uint `gorm:"primaryKey"`
	ReimburseId uint `gorm:"index"`
	AttachId    uint
	Attach      attach.Attach `gorm:"foreignKey:AttachId"`
}

func (r *AttachRelation) TableName() string {
	return "reimburse_attach_relation"
}

type AttachRelRepo struct {
	*database.BaseRepository[AttachRelation]
}

func NewAttachRelRepo(db *gorm.DB) *AttachRelRepo {
	return &AttachRelRepo{
		BaseRepository: database.NewBaseRepository[AttachRelation](db),
	}
}

func (r *AttachRelRepo) FindByBillID(ctx context.Context, billID uint) ([]AttachRelation, error) {
	res := make([]AttachRelation, 0)
	if err := r.GetDB(ctx).WithContext(ctx).Preload("Attach").
		Find(&res, "reimburse_attach_relation.reimburse_id = ?", billID).Error; err != nil {
		return nil, errs.Wrap(err)
	}
	return res, nil
}

func (r *AttachRelRepo) DeleteByBillIDAndAttachIDs(ctx context.Context, billID uint, attachIDs []uint) error {
	if len(attachIDs) == 0 {
		return nil
	}
	var t AttachRelation
	return errs.Wrap(r.GetDB(ctx).WithContext(ctx).
		Where("reimburse_id = ? and attach_id in ?", billID, attachIDs).
		Delete(&t).Error)
}

// 报销单与附件的关联由本模块自行维护，附件模块只提供附件本身的变更能力。
type AttachRelService struct {
	relRepo   *AttachRelRepo
	attachSrv *attach.Service
}

func NewAttachRelService(db *gorm.DB, attachSrv *attach.Service) *AttachRelService {
	return &AttachRelService{
		relRepo:   NewAttachRelRepo(db),
		attachSrv: attachSrv,
	}
}

func (s *AttachRelService) FindAttachments(ctx context.Context, billID uint) ([]attach.Attach, error) {
	rels, err := s.relRepo.FindByBillID(ctx, billID)
	if err != nil {
		return nil, err
	}
	attaches := make([]attach.Attach, 0, len(rels))
	for _, rel := range rels {
		attaches = append(attaches, rel.Attach)
	}
	return attaches, nil
}

func (s *AttachRelService) UpdateAttachments(ctx context.Context, billID uint, dtoes []attach.AttachDTO) error {
	rels, err := s.relRepo.FindByBillID(ctx, billID)
	if err != nil {
		return err
	}

	currentIDs := make(map[uint]bool, len(rels))
	for _, rel := range rels {
		currentIDs[rel.Attach.ID] = true
	}

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
			rel := &AttachRelation{
				ReimburseId: billID,
				AttachId:    attachID,
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
