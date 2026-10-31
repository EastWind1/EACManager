package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"backend-go/pkg/util"
	"context"
	"fmt"
	"mime/multipart"
	"strconv"
	"strings"
	"time"

	"github.com/gofiber/fiber/v3/log"
)

type Service struct {
	reimburseRepo *Repository
	attachSrv     *attach.Service
	relSrv        *AttachRelService
	mapper        *Mapper
}

func NewService(
	reimburseRepo *Repository,
	attachSrv *attach.Service,
	relSrv *AttachRelService,
	mapper *Mapper,
) *Service {
	return &Service{
		reimburseRepo: reimburseRepo,
		attachSrv:     attachSrv,
		relSrv:        relSrv,
		mapper:        mapper,
	}
}

func (s *Service) GenerateNumber() string {
	return "R" + strconv.Itoa(int(util.GenIntID()))
}

// validNumber 与 Java ReimburseService.validNumber 保持一致：拒绝路径分隔符和单独的 ..
func validNumber(number string) error {
	if strings.TrimSpace(number) == "" {
		return errs.NewBizError("单号为空")
	}
	if strings.ContainsAny(number, `/\`) {
		return errs.NewBizError(`单号不能包含 / \`)
	}
	if number == ".." {
		return errs.NewBizError("单号不能为 ..")
	}
	return nil
}

func (s *Service) FindByID(ctx context.Context, id uint) (*DTO, error) {
	if id == 0 {
		return nil, errs.NewBizError("ID不能为空")
	}
	bill, err := s.reimburseRepo.FindFullById(ctx, id)
	if err != nil {
		return nil, err
	}
	if bill == nil {
		return nil, errs.NewBizError("单据不存在")
	}
	attachments, err := s.relSrv.FindAttachments(ctx, bill.ID)
	if err != nil {
		return nil, err
	}
	return bill.ToDTO(attachments), nil
}

func (s *Service) Create(ctx context.Context, dto *DTO) (*DTO, error) {
	if dto.ID != 0 {
		return nil, errs.NewBizError("单据 ID 不为空")
	}
	entity := dto.ToEntity()
	if entity.Number == "" {
		entity.Number = s.GenerateNumber()
	} else if err := validNumber(entity.Number); err != nil {
		return nil, err
	}
	entity.State = Created

	var attaches []attach.Attach
	err := s.reimburseRepo.Transaction(ctx, func(txCtx context.Context) error {
		// 单号唯一性交给唯一索引，冲突统一在 Insert 里转成业务错误
		if err := s.reimburseRepo.Insert(txCtx, entity); err != nil {
			return err
		}
		if err := s.relSrv.UpdateAttachments(txCtx, entity.ID, dto.Attachments); err != nil {
			return err
		}
		// 附件在同一事务里读取，省掉提交后的一次往返
		var err error
		attaches, err = s.relSrv.FindAttachments(txCtx, entity.ID)
		return err
	})
	if err != nil {
		return nil, err
	}
	return entity.ToDTO(attaches), nil
}

func (s *Service) Update(ctx context.Context, dto *DTO) (*DTO, error) {
	if dto.ID == 0 {
		return nil, errs.NewBizError("id不能为空")
	}

	entity := dto.ToEntity()

	var attaches []attach.Attach
	err := s.reimburseRepo.Transaction(ctx, func(txCtx context.Context) error {
		bill, err := s.reimburseRepo.FindByID(txCtx, dto.ID)
		if err != nil {
			return err
		}
		if bill == nil {
			return errs.NewBizError("单据不存在")
		}
		if entity.Number != bill.Number {
			if err := validNumber(entity.Number); err != nil {
				return err
			}
		}
		entity.State = bill.State
		if err = s.relSrv.UpdateAttachments(txCtx, entity.ID, dto.Attachments); err != nil {
			return err
		}
		if err = s.reimburseRepo.Updates(txCtx, entity); err != nil {
			return err
		}
		attaches, err = s.relSrv.FindAttachments(txCtx, entity.ID)
		return err
	})
	if err != nil {
		return nil, err
	}
	return entity.ToDTO(attaches), nil
}

// GenerateByFile 上传并解析文件生成单据 DTO，不落库。
func (s *Service) GenerateByFile(ctx context.Context, file *multipart.FileHeader) (*DTO, error) {
	attaches, err := s.attachSrv.Upload(ctx, AttachNamespace, []*multipart.FileHeader{file})
	if err != nil {
		return nil, err
	}
	attachment := attaches[0]
	dto, err := s.mapper.Map(&attachment)
	if err != nil {
		return nil, err
	}
	dto.Attachments = attaches
	return dto, nil
}

func (s *Service) FindByParam(ctx context.Context, param *QueryParam) (*result.PageResult[DTO], error) {
	if param == nil {
		return nil, errs.NewBizError("查询参数为空")
	}

	res, err := s.reimburseRepo.FindByParam(ctx, param)
	if err != nil {
		return nil, err
	}

	return result.NewDTOPageResult(res, ToBaseDTOs), nil
}

// Delete 只允许删除创建状态的单据，逐项返回结果。
func (s *Service) Delete(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.reimburseRepo.Transaction(ctx, func(txCtx context.Context) error {
			bill, err := s.reimburseRepo.FindByID(txCtx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if bill.State != Created {
				return errs.NewBizError("非创建状态不能删除")
			}
			if err = s.relSrv.UpdateAttachments(txCtx, bill.ID, nil); err != nil {
				return err
			}
			deleted, err := s.reimburseRepo.Delete(txCtx, bill.ID)
			if err != nil {
				return err
			}
			if !deleted {
				return errs.NewBizError("数据已被更改，稍后重试")
			}
			return nil
		})
		return nil, err
	}), nil
}

// transition 状态流转的公共骨架：读当前状态 → 校验 → 只更新 state 列
func (s *Service) transition(
	ctx context.Context,
	ids []uint,
	apply func(bill *Reimburse) error,
) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.reimburseRepo.Transaction(ctx, func(txCtx context.Context) error {
			bill, err := s.reimburseRepo.FindByID(txCtx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if err = apply(bill); err != nil {
				return err
			}
			log.Infof("报销单 %v 状态变更为 %v", bill.Number, bill.State)
			return s.reimburseRepo.UpdateState(txCtx, bill)
		})
		return nil, err
	}), nil
}

func (s *Service) Process(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	return s.transition(ctx, ids, func(bill *Reimburse) error {
		if bill.State != Created {
			return errs.NewBizError("非创建状态不能提交")
		}
		bill.State = Processing
		return nil
	})
}

func (s *Service) Finish(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	return s.transition(ctx, ids, func(bill *Reimburse) error {
		if bill.State != Processing {
			return errs.NewBizError("非处理状态不能完成")
		}
		bill.State = Finished
		return nil
	})
}

func (s *Service) CancelProcess(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	return s.transition(ctx, ids, func(bill *Reimburse) error {
		if bill.State != Processing {
			return errs.NewBizError("非处理中状态不能取消")
		}
		bill.State = Created
		return nil
	})
}

func (s *Service) CancelFinish(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	return s.transition(ctx, ids, func(bill *Reimburse) error {
		if bill.State != Finished {
			return errs.NewBizError("非完成状态不能取消")
		}
		bill.State = Processing
		return nil
	})
}

func (s *Service) Export(ctx context.Context, ids []uint) (string, error) {
	if len(ids) == 0 {
		return "", errs.NewBizError("ID 不能为空")
	}

	// 主表 + 明细一次取回，不再逐单据 Preload
	reimbursements, err := s.reimburseRepo.FindAllWithDetails(ctx, ids)
	if err != nil {
		return "", err
	}
	if len(reimbursements) == 0 {
		return "", errs.NewBizError("单据不存在")
	}

	// 一次取回全部单据的附件
	rels, err := s.relSrv.FindByBillIDs(ctx, ids)
	if err != nil {
		return "", err
	}

	tempDir, err := s.attachSrv.CreateTempDir("export")
	if err != nil {
		return "", err
	}

	ops := make([]attach.FileOp, 0, len(rels))
	rows := make([][]string, 0, len(reimbursements)+2)

	rows = append(rows, []string{"单据编号", "摘要", "总额", "报销日期", "备注"})
	var totalAmount float64

	for _, reimbursement := range reimbursements {
		dateStr := ""
		if reimbursement.ReimburseDate != nil {
			dateStr = reimbursement.ReimburseDate.Format("2006-01-02")
		}

		var detailStr strings.Builder
		for _, detail := range reimbursement.Details {
			detailStr.WriteString(fmt.Sprintf("%s : %.2f ; ", detail.Name, detail.Amount))
		}

		rows = append(rows, []string{
			reimbursement.Number,
			reimbursement.Summary,
			fmt.Sprintf("%.2f", reimbursement.TotalAmount),
			dateStr,
			detailStr.String(),
		})

		totalAmount += reimbursement.TotalAmount
	}

	for _, reimbursement := range reimbursements {
		curDir, err := attach.SafeJoin(tempDir, reimbursement.Number)
		if err != nil {
			return "", errs.NewBizError("单号不合法")
		}
		for _, attachment := range rels[reimbursement.ID] {
			originPath, err := s.attachSrv.GetAbsolutePathByRela(attachment.RelativePath)
			if err != nil {
				return "", err
			}
			target, err := uniqueTargetPath(curDir, attachment.Name)
			if err != nil {
				return "", errs.NewBizError("附件名不合法")
			}
			ops = append(ops, attach.FileOp{
				Type:   attach.FileOpCopy,
				Origin: originPath,
				Target: target,
			})
		}
	}

	rows = append(rows, []string{"", "合计", fmt.Sprintf("%.2f", totalAmount), "", ""})

	excelPath, err := attach.SafeJoin(tempDir, fmt.Sprintf("导出结果%s.xlsx", time.Now().Format("20060102150405")))
	if err != nil {
		return "", err
	}
	if err = attach.GenerateExcelFromList(rows, excelPath); err != nil {
		return "", err
	}

	if err = attach.Exec(ops); err != nil {
		return "", err
	}
	zipPath, err := attach.Zip(tempDir, "")
	if err != nil {
		return "", err
	}
	return zipPath, nil
}

// uniqueTargetPath 处理导出目录下的同名附件：name、1-name、2-name ……
func uniqueTargetPath(dir, name string) (string, error) {
	targetPath, err := attach.SafeJoin(dir, name)
	if err != nil {
		return "", err
	}
	repeatCount := 1
	for attach.Exists(targetPath) {
		targetPath, err = attach.SafeJoin(dir, fmt.Sprintf("%d-%s", repeatCount, name))
		if err != nil {
			return "", err
		}
		repeatCount++
	}
	return targetPath, nil
}
