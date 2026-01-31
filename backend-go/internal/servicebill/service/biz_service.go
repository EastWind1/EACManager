package service

import (
	"backend-go/internal/attach/files"
	attachModel "backend-go/internal/attach/model"
	attachService "backend-go/internal/attach/service"
	"backend-go/internal/common/cache"
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"backend-go/internal/servicebill/model"
	"backend-go/internal/servicebill/repository"
	"context"
	"fmt"
	"math"
	"mime/multipart"
	"path/filepath"
	"time"
)

type BizService struct {
	cache        cache.Cache
	billRepo     *repository.ServiceBillRepository
	attachSrv    *attachService.AttachmentService
	attachMapSrv *attachService.AttachMapService
}

func NewBizService(
	cache cache.Cache,
	billRepo *repository.ServiceBillRepository,
	attachSrv *attachService.AttachmentService,
	attachMapSrv *attachService.AttachMapService,
) *BizService {
	return &BizService{
		cache:        cache,
		billRepo:     billRepo,
		attachSrv:    attachSrv,
		attachMapSrv: attachMapSrv,
	}
}

func (s *BizService) ValidateAmount(bill *model.ServiceBill) error {
	totalAmount := 0.0
	for _, detail := range bill.Details {
		expectedSubtotal := detail.Quantity * detail.UnitPrice
		if math.Abs(detail.Subtotal-expectedSubtotal) > 0.01 {
			return errs.NewBizError("明细金额有误")
		}
		totalAmount += detail.Subtotal
	}
	if math.Abs(totalAmount-bill.TotalAmount) > 0.01 {
		return errs.NewBizError("总金额有误")
	}
	return nil
}

func (s *BizService) GenerateNumber() string {
	timestamp := time.Now().Format("20060102")
	randomNum := time.Now().UnixNano() % 1000
	if randomNum < 0 {
		randomNum = -randomNum
	}
	return fmt.Sprintf("S%s%04d", timestamp, randomNum)
}

func (s *BizService) FindByID(ctx context.Context, id uint) (*model.ServiceBillDTO, error) {
	if id == 0 {
		return nil, errs.NewBizError("ID不能为空")
	}
	bill, err := s.billRepo.FindFullById(ctx, id)
	if err != nil {
		return nil, err
	}
	if bill == nil {
		return nil, errs.NewBizError("单据不存在")
	}
	attaches, err := s.attachSrv.GetByBill(ctx, bill.ID, attachModel.BillTypeServiceBill)
	dto := bill.ToDTO(attaches)
	return dto, nil
}

func (s *BizService) FindByParam(ctx context.Context, param *model.ServiceBillQueryParam) (*result.PageResult[model.ServiceBillDTO], error) {
	if param == nil {
		return nil, errs.NewBizError("查询参数为空")
	}

	res, err := s.billRepo.FindByParam(ctx, param)
	if err != nil {
		return nil, err
	}

	return result.NewPageResultFromDB(res, model.ToBaseDTOs), nil
}

func (s *BizService) Create(ctx context.Context, dto *model.ServiceBillDTO) (*model.ServiceBillDTO, error) {
	if dto.ID != 0 {
		return nil, errs.NewBizError("单据 ID 自动生成")
	}
	if dto.Number == "" {
		dto.Number = s.GenerateNumber()
	}

	entity := dto.ToEntity()
	if err := s.ValidateAmount(entity); err != nil {
		return nil, err
	}

	err := s.billRepo.Transaction(ctx, func(tx context.Context) error {
		exist, err := s.billRepo.ExistsByNumber(tx, entity.Number)
		if err != nil {
			return err
		}
		if exist {
			return errs.NewBizError("单据编号已存在")
		}
		if err = s.billRepo.Create(tx, entity); err != nil {
			return err
		}
		return s.attachSrv.UpdateRelativeAttach(tx, entity.ID, entity.Number, attachModel.BillTypeServiceBill, &dto.Attachments)
	})

	if err != nil {
		return nil, err
	}

	attaches, err := s.attachSrv.GetByBill(ctx, entity.ID, attachModel.BillTypeServiceBill)
	if err != nil {
		return nil, err
	}

	return entity.ToDTO(attaches), nil
}

func (s *BizService) Update(ctx context.Context, dto *model.ServiceBillDTO) (*model.ServiceBillDTO, error) {
	if dto.ID == 0 {
		return nil, errs.NewBizError("单据 ID 为空")
	}

	entity := dto.ToEntity()
	if err := s.ValidateAmount(entity); err != nil {
		return nil, err
	}

	err := s.billRepo.Transaction(ctx, func(tx context.Context) error {
		bill, err := s.billRepo.FindByID(tx, entity.ID)
		if err != nil {
			return err
		}
		if bill == nil {
			return errs.NewBizError("单据不存在")
		}
		if err = s.billRepo.Updates(tx, entity); err != nil {
			return err
		}
		return s.attachSrv.UpdateRelativeAttach(tx, entity.ID, entity.Number, attachModel.BillTypeServiceBill, &dto.Attachments)
	})

	if err != nil {
		return nil, err
	}

	attaches, err := s.attachSrv.GetByBill(ctx, entity.ID, attachModel.BillTypeServiceBill)
	if err != nil {
		return nil, err
	}
	return entity.ToDTO(attaches), nil
}

func (s *BizService) Delete(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.billRepo.Transaction(ctx, func(tx context.Context) error {
			bill, err := s.billRepo.FindByID(tx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if bill.State != model.ServiceBillStateCreated {
				return errs.NewBizError("非创建状态不能删除")
			}
			if err = s.billRepo.DeleteByID(tx, id); err != nil {
				return err
			}
			return s.attachSrv.UpdateRelativeAttach(tx, bill.ID, bill.Number, attachModel.BillTypeReimbursement, nil)
		})
		return nil, err
	}), nil
}

func (s *BizService) Process(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.billRepo.Transaction(ctx, func(tx context.Context) error {
			bill, err := s.billRepo.FindByID(tx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if bill.State != model.ServiceBillStateCreated {
				return errs.NewBizError("非创建状态的单据不能处理")
			}
			bill.State = model.ServiceBillStateProcessing
			return s.billRepo.Updates(tx, bill)
		})
		return nil, err
	}), nil
}

func (s *BizService) Processed(ctx context.Context, ids []uint, processedDate *time.Time) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	if processedDate == nil {
		now := time.Now()
		processedDate = &now
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.billRepo.Transaction(ctx, func(tx context.Context) error {
			bill, err := s.billRepo.FindByID(tx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if bill.State != model.ServiceBillStateProcessing {
				return errs.NewBizError("非处理中状态的单据不能处理完成")
			}
			bill.State = model.ServiceBillStateProcessed
			bill.ProcessedDate = processedDate
			return s.billRepo.Updates(tx, bill)
		})
		return nil, err
	}), nil
}

func (s *BizService) Finish(ctx context.Context, ids []uint, finishedDate *time.Time) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	if finishedDate == nil {
		now := time.Now()
		finishedDate = &now
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.billRepo.Transaction(ctx, func(tx context.Context) error {
			bill, err := s.billRepo.FindByID(tx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if bill.State != model.ServiceBillStateProcessed {
				return errs.NewBizError("非处理完成状态的单据不能完成")
			}
			bill.State = model.ServiceBillStateFinished
			bill.FinishedDate = finishedDate
			return s.billRepo.Updates(tx, bill)
		})
		return nil, err
	}), nil
}

func (s *BizService) GenerateByFile(ctx context.Context, file *multipart.FileHeader) (*model.ServiceBillDTO, error) {
	attaches, err := s.attachSrv.UploadTemps(&[]*multipart.FileHeader{file})
	if err != nil {
		return nil, err
	}
	attach := (*attaches)[0]
	data, err := s.attachMapSrv.MapTo(&attach)
	if err != nil {
		return nil, err
	}
	if bill, ok := data.(*model.ServiceBillDTO); ok {
		bill.Attachments = *attaches
		return bill, nil
	}

	return nil, errs.NewBizError("转换失败")

}

func (s *BizService) Export(ctx context.Context, ids []uint) (string, error) {
	if ids == nil || len(ids) == 0 {
		return "", errs.NewBizError("ID 不能为空")
	}

	bills, err := s.billRepo.FindAll(ctx, ids)
	if bills == nil || len(*bills) == 0 {
		return "", errs.NewBizError("单据不存在")
	}

	// 创建临时目录
	tempDir, err := s.attachSrv.CreateTempDir(s.cache, "export")
	if err != nil {
		return "", err
	}

	var ops []attachModel.FileOp
	var rows [][]string

	// 表头
	rows = append(rows, []string{"单据编号", "状态", "项目名称", "项目地址", "总额", "安装完成日期", "备注"})
	var totalAmount float64

	// 遍历生成 excel行，并拷贝附件
	for _, bill := range *bills {
		// 构建详情字符串
		detailStr := ""
		for _, detail := range bill.Details {
			detailStr += fmt.Sprintf("%s : %.2f * %.2f ; ", detail.Device, detail.UnitPrice, detail.Quantity)
		}
		processDateStr := ""
		if bill.ProcessedDate != nil {
			processDateStr = bill.ProcessedDate.Format("2006-01-02")
		}
		// 添加行数据
		rows = append(rows, []string{
			bill.Number,
			bill.State.String(),
			bill.ProjectName,
			bill.ProjectAddress,
			fmt.Sprintf("%.2f", bill.TotalAmount),
			processDateStr,
			detailStr,
		})

		totalAmount += bill.TotalAmount

		// 获取当前单据的所有附件
		attachments, err := s.attachSrv.GetByBill(ctx, bill.ID, attachModel.BillTypeServiceBill)
		if err != nil {
			return "", err
		}

		// 创建当前单据附件文件夹
		curDir := filepath.Join(tempDir, bill.Number)
		for _, attachment := range *attachments {
			// 获取原始附件路径
			originPath, err := s.attachSrv.GetAbsolutePath(attachment.RelativePath, false)
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
