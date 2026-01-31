package service

import (
	attachModel "backend-go/internal/attach/model"
	"backend-go/internal/common/cache"
	"context"
	"fmt"
	"math/rand"
	"path/filepath"
	"time"

	"backend-go/internal/attach/files"
	"backend-go/internal/attach/service"
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"backend-go/internal/reimburse/model"
	"backend-go/internal/reimburse/repository"
)

type ReimburseService struct {
	reimburseRepo   *repository.ReimburseRepository
	attachService   *service.AttachmentService
	cache           cache.Cache
	reimburseMapper *ReimburseMapper
}

type ReimburseMapper struct{}

func NewReimburseService(
	reimburseRepo *repository.ReimburseRepository,
	attachService *service.AttachmentService,
	cache cache.Cache,
) *ReimburseService {
	return &ReimburseService{
		reimburseRepo: reimburseRepo,
		attachService: attachService,
		cache:         cache,
	}
}

func (s *ReimburseService) GenerateNumber() string {
	timestamp := time.Now().Format("20060102")
	randomNum := rand.Intn(1000)
	return fmt.Sprintf("R%s%04d", timestamp, randomNum)
}

func (s *ReimburseService) FindByID(ctx context.Context, id uint) (*model.ReimbursementDTO, error) {
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
	attachments, err := s.attachService.GetByBill(ctx, bill.ID, attachModel.BillTypeReimbursement)
	if err != nil {
		return nil, errs.NewBizError("获取附件失败")
	}
	return bill.ToDTO(attachments), nil
}

func (s *ReimburseService) Create(ctx context.Context, dto *model.ReimbursementDTO) (*model.ReimbursementDTO, error) {
	if dto.ID != 0 {
		dto.ID = 0
	}
	entity := model.Reimbursement{
		ID:            dto.ID,
		Number:        dto.Number,
		State:         dto.State,
		Summary:       dto.Summary,
		TotalAmount:   dto.TotalAmount,
		ReimburseDate: dto.ReimburseDate,
		Remark:        dto.Remark,
	}
	if entity.Number == "" {
		entity.Number = s.GenerateNumber()
	}
	err := s.reimburseRepo.Transaction(ctx, func(tx context.Context) error {
		exists, _ := s.reimburseRepo.ExistsByNumber(ctx, dto.Number)
		if exists {
			return errs.NewBizError("单据编号已存在")
		}
		if err := s.reimburseRepo.Create(tx, &entity); err != nil {
			return err
		}

		if err := s.attachService.UpdateRelativeAttach(tx, entity.ID, entity.Number, attachModel.BillTypeReimbursement, &dto.Attachments); err != nil {
			return err
		}
		return nil
	})

	if err != nil {
		return nil, err
	}

	attaches, err := s.attachService.GetByBill(ctx, entity.ID, attachModel.BillTypeReimbursement)
	if err != nil {
		return nil, err
	}
	return entity.ToDTO(attaches), nil
}

func (s *ReimburseService) Update(ctx context.Context, dto *model.ReimbursementDTO) (*model.ReimbursementDTO, error) {
	if dto.ID == 0 {
		return nil, errs.NewBizError("id不能为空")
	}

	entity := model.Reimbursement{
		ID:            dto.ID,
		Number:        dto.Number,
		State:         dto.State,
		Summary:       dto.Summary,
		TotalAmount:   dto.TotalAmount,
		ReimburseDate: dto.ReimburseDate,
		Remark:        dto.Remark,
	}

	if len(dto.Details) > 0 {
		details := make([]model.ReimburseDetail, len(dto.Details))
		for i, detail := range dto.Details {
			details[i] = model.ReimburseDetail{
				ID:     detail.ID,
				Name:   detail.Name,
				Amount: detail.Amount,
			}
		}
		entity.Details = details
	}

	err := s.reimburseRepo.Transaction(ctx, func(tx context.Context) error {
		bill, err := s.reimburseRepo.FindByID(tx, dto.ID)
		if err != nil {
			return err
		}
		if bill == nil {
			return errs.NewBizError("单据不存在")
		}
		err = s.attachService.UpdateRelativeAttach(tx, entity.ID, entity.Number, attachModel.BillTypeReimbursement, &dto.Attachments)
		if err != nil {
			return err
		}
		return s.reimburseRepo.Updates(tx, bill)
	})

	if err != nil {
		return nil, err
	}

	attaches, err := s.attachService.GetByBill(ctx, entity.ID, attachModel.BillTypeReimbursement)
	if err != nil {
		return nil, err
	}
	return entity.ToDTO(attaches), nil
}

func (s *ReimburseService) FindByParam(ctx context.Context, param *model.ReimburseQueryParam) (*result.PageResult[model.ReimbursementDTO], error) {
	if param == nil {
		return nil, errs.NewBizError("查询参数为空")
	}

	res, err := s.reimburseRepo.FindByParam(ctx, param)
	if err != nil {
		return nil, err
	}

	return result.NewPageResultFromDB(res, model.ToBaseDTOs), nil
}

func (s *ReimburseService) Delete(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.reimburseRepo.Transaction(ctx, func(tx context.Context) error {
			bill, err := s.reimburseRepo.FindByID(tx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if bill.State != model.ReimburseStateCreated {
				return errs.NewBizError("非创建状态不能删除")
			}
			if err = s.reimburseRepo.DeleteByID(tx, id); err != nil {
				return err
			}
			return s.attachService.UpdateRelativeAttach(tx, bill.ID, bill.Number, attachModel.BillTypeReimbursement, nil)
		})
		return nil, err
	}), nil
}

func (s *ReimburseService) Process(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.reimburseRepo.Transaction(ctx, func(tx context.Context) error {
			bill, err := s.reimburseRepo.FindByID(tx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if bill.State != model.ReimburseStateCreated {
				return errs.NewBizError("非创建状态不能提交")
			}
			bill.State = model.ReimburseStateProcessing
			return s.reimburseRepo.Updates(tx, bill)
		})
		return nil, err
	}), nil
}

func (s *ReimburseService) Finish(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.reimburseRepo.Transaction(ctx, func(tx context.Context) error {
			bill, err := s.reimburseRepo.FindByID(tx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if bill.State != model.ReimburseStateProcessing {
				return errs.NewBizError("非处理状态不能完成")
			}
			bill.State = model.ReimburseStateFinished
			return s.reimburseRepo.Updates(tx, bill)
		})
		return nil, err
	}), nil
}

func (s *ReimburseService) Export(ctx context.Context, ids []uint) (string, error) {
	if ids == nil || len(ids) == 0 {
		return "", errs.NewBizError("ID 不能为空")
	}

	reimbursements, err := s.reimburseRepo.FindAll(ctx, ids)
	if reimbursements == nil || len(*reimbursements) == 0 {
		return "", errs.NewBizError("单据不存在")
	}

	// 创建临时目录
	tempDir, err := s.attachService.CreateTempDir(s.cache, "export")
	if err != nil {
		return "", err
	}

	var ops []attachModel.FileOp
	var rows [][]string

	// 表头
	rows = append(rows, []string{"单据编号", "摘要", "总额", "报销日期", "备注"})
	var totalAmount float64

	// 遍历生成 excel行，并拷贝附件
	for _, reimbursement := range *reimbursements {
		dateStr := ""
		if reimbursement.ReimburseDate != nil {
			dateStr = reimbursement.ReimburseDate.Format("2006-01-02")
		}

		// 构建详情字符串
		detailStr := ""
		for _, detail := range reimbursement.Details {
			detailStr += fmt.Sprintf("%s : %.2f ; ", detail.Name, detail.Amount)
		}

		// 添加行数据
		rows = append(rows, []string{
			reimbursement.Number,
			reimbursement.Summary,
			fmt.Sprintf("%.2f", reimbursement.TotalAmount),
			dateStr,
			detailStr,
		})

		totalAmount += reimbursement.TotalAmount

		// 获取当前单据的所有附件
		attachments, err := s.attachService.GetByBill(ctx, reimbursement.ID, attachModel.BillTypeReimbursement)
		if err != nil {
			return "", err
		}

		// 创建当前单据附件文件夹
		curDir := filepath.Join(tempDir, reimbursement.Number)
		for _, attachment := range *attachments {
			// 获取原始附件路径
			originPath, err := s.attachService.GetAbsolutePath(attachment.RelativePath, false)
			if err != nil {
				return "", err
			}

			// 设置目标路径
			targetPath := filepath.Join(curDir, attachment.Name)

			// 处理可能的重名
			repeatCount := 1
			for files.Exists(targetPath) {
				targetPath = fmt.Sprintf("%s/%d-%s", curDir, repeatCount, attachment.Name)
				repeatCount++
			}
			// 添加文件操作到列表
			ops = append(ops, attachModel.FileOp{
				Type:   attachModel.FileOpCopy,
				Origin: originPath,
				Target: targetPath,
			})
		}
	}

	// 表合计
	rows = append(rows, []string{"", "合计", fmt.Sprintf("%.2f", totalAmount), "", ""})

	// 生成Excel文件
	excelPath := filepath.Join(tempDir, fmt.Sprintf("导出结果%s.xlsx", time.Now().Format("20060102150405")))
	if err = files.GenerateExcelFromList(&rows, excelPath); err != nil {
		return "", errs.NewBizError("生成Excel失败: " + err.Error())
	}

	if err = files.Exec(s.cache, &ops); err != nil {
		return "", errs.NewBizError("文件操作失败: " + err.Error())
	}
	zipPath, err := files.Zip(tempDir, "")
	if err != nil {
		return "", err
	}
	return zipPath, nil
}
