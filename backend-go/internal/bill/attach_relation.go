package bill

import (
	"backend-go/internal/attach"
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"context"

	"gorm.io/gorm"
)

// AttachNamespace 只用于隔离附件存储，附件模块不解释其含义。
const AttachNamespace = "SERVICE_BILL"

const relationBatchSize = database.RelationBatchSize

type AttachRelation struct {
	ID            uint `gorm:"primaryKey"`
	ServiceBillId uint `gorm:"index"`
	AttachId      uint
	Attach        attach.Attach `gorm:"foreignKey:AttachId"`
}

func (r *AttachRelation) TableName() string {
	return "service_bill_attach_relation"
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
	res, err := gorm.G[AttachRelation](r.Ctx(ctx)).
		Preload("Attach", nil).
		Where("service_bill_id = ?", billID).
		Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return res, nil
}

// FindByBillIDs 批量查询多张单据的关联，按单据 ID 分组（导出用，避免逐单据往返）
func (r *AttachRelRepo) FindByBillIDs(ctx context.Context, billIDs []uint) (map[uint][]AttachRelation, error) {
	res := make(map[uint][]AttachRelation, len(billIDs))
	if len(billIDs) == 0 {
		return res, nil
	}
	rels, err := gorm.G[AttachRelation](r.Ctx(ctx)).
		Preload("Attach", nil).
		Where("service_bill_id IN ?", billIDs).
		Order("id").
		Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	for _, rel := range rels {
		res[rel.ServiceBillId] = append(res[rel.ServiceBillId], rel)
	}
	return res, nil
}

func (r *AttachRelRepo) CreateAll(ctx context.Context, rels []AttachRelation) error {
	if len(rels) == 0 {
		return nil
	}
	return errs.Wrap(gorm.G[AttachRelation](r.Ctx(ctx)).CreateInBatches(ctx, &rels, relationBatchSize))
}

func (r *AttachRelRepo) DeleteByBillIDAndAttachIDs(ctx context.Context, billID uint, attachIDs []uint) error {
	if len(attachIDs) == 0 {
		return nil
	}
	_, err := gorm.G[AttachRelation](r.Ctx(ctx)).
		Where("service_bill_id = ? and attach_id in ?", billID, attachIDs).
		Delete(ctx)
	return errs.Wrap(err)
}

// RelService 服务单与附件的关联由本模块自行维护；附件的落库、提交与删除交给附件模块，
// 差异对比只是「目标集合」与「本单据当前关联」的集合运算。
type RelService struct {
	relRepo   *AttachRelRepo
	attachSrv *attach.Service
}

func NewAttachRelService(db *gorm.DB, attachSrv *attach.Service) *RelService {
	return &RelService{
		relRepo:   NewAttachRelRepo(db),
		attachSrv: attachSrv,
	}
}

func (s *RelService) FindAttachments(ctx context.Context, billID uint) ([]attach.Attach, error) {
	rels, err := s.relRepo.FindByBillID(ctx, billID)
	if err != nil {
		return nil, err
	}
	return toAttachments(rels), nil
}

func (s *RelService) FindByBillIDs(ctx context.Context, billIDs []uint) (map[uint][]attach.Attach, error) {
	rels, err := s.relRepo.FindByBillIDs(ctx, billIDs)
	if err != nil {
		return nil, err
	}
	res := make(map[uint][]attach.Attach, len(rels))
	for billID, billRels := range rels {
		res[billID] = toAttachments(billRels)
	}
	return res, nil
}

func toAttachments(rels []AttachRelation) []attach.Attach {
	attaches := make([]attach.Attach, 0, len(rels))
	for _, rel := range rels {
		attaches = append(attaches, rel.Attach)
	}
	return attaches
}

func (s *RelService) UpdateAttachments(ctx context.Context, billID uint, dtoes []attach.DTO) error {
	rels, err := s.relRepo.FindByBillID(ctx, billID)
	if err != nil {
		return err
	}

	currentIDs := make(map[uint]struct{}, len(rels))
	for _, rel := range rels {
		currentIDs[rel.AttachId] = struct{}{}
	}

	addedIDs := make([]uint, 0, len(dtoes))
	targetIDs := make(map[uint]struct{}, len(dtoes))
	for _, dto := range dtoes {
		id := uint(dto.ID)
		if id == 0 {
			continue
		}
		if _, ok := targetIDs[id]; ok {
			continue
		}
		targetIDs[id] = struct{}{}
		if _, ok := currentIDs[id]; !ok {
			addedIDs = append(addedIDs, id)
		}
	}
	removedIDs := make([]uint, 0, len(rels))
	for _, rel := range rels {
		if _, ok := targetIDs[rel.AttachId]; !ok {
			removedIDs = append(removedIDs, rel.AttachId)
		}
	}
	if len(addedIDs) == 0 && len(removedIDs) == 0 {
		return nil
	}

	// 先提交新增附件，再断开关联、删除附件与文件
	if len(addedIDs) > 0 {
		if err = s.attachSrv.Persist(ctx, addedIDs); err != nil {
			return err
		}
		newRels := make([]AttachRelation, 0, len(addedIDs))
		for _, attachID := range addedIDs {
			newRels = append(newRels, AttachRelation{
				ServiceBillId: billID,
				AttachId:      attachID,
			})
		}
		if err = s.relRepo.CreateAll(ctx, newRels); err != nil {
			return err
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
