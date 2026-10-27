package bill

import (
	"backend-go/internal/attach"
	"backend-go/pkg/cache"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"backend-go/pkg/util"
	"context"
	"fmt"
	"math"
	"mime/multipart"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gofiber/fiber/v3/log"
)

type Service struct {
	cache     cache.Cache
	billRepo  *Repository
	attachSrv *attach.Service
	relSrv    *RelService
	mapper    *Mapper
}

func NewService(
	cache cache.Cache,
	billRepo *Repository,
	attachSrv *attach.Service,
	relSrv *RelService,
	mapper *Mapper,
) *Service {
	return &Service{
		cache:     cache,
		billRepo:  billRepo,
		attachSrv: attachSrv,
		relSrv:    relSrv,
		mapper:    mapper,
	}
}

func (s *Service) ValidateAmount(bill *ServiceBill) error {
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

func (s *Service) GenerateNumber() string {
	return "S" + strconv.Itoa(int(util.GenIntID()))
}

func (s *Service) FindByID(ctx context.Context, id uint) (*DTO, error) {
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
	attaches, err := s.relSrv.FindAttachments(ctx, bill.ID)
	if err != nil {
		return nil, err
	}
	return bill.ToDTO(attaches), nil
}

func (s *Service) FindByParam(ctx context.Context, param *QueryParam) (*result.PageResult[DTO], error) {
	if param == nil {
		return nil, errs.NewBizError("查询参数为空")
	}

	res, err := s.billRepo.FindByParam(ctx, param)
	if err != nil {
		return nil, err
	}

	return result.NewDTOPageResult(res, ToBaseDTOs), nil
}

func (s *Service) Create(ctx context.Context, dto *DTO) (*DTO, error) {
	if dto.ID != 0 {
		return nil, errs.NewBizError("单据 ID 自动生成")
	}

	entity := dto.ToEntity()
	if err := s.ValidateAmount(entity); err != nil {
		return nil, err
	}
	entity.State = Created

	// 未指定单号时由服务端生成；生成器是毫秒内自增的雪花 ID，重复只可能来自并发写入，
	// 交由唯一索引兜底，冲突时重新生成，不再先查后插（那是两次往返，且本身仍有竞态）
	if entity.Number == "" {
		entity.Number = s.GenerateNumber()
	}

	var attaches []attach.Attach
	err := s.billRepo.Transaction(ctx, func(txCtx context.Context) error {
		if err := s.billRepo.Insert(txCtx, entity); err != nil {
			return err
		}
		if err := s.relSrv.UpdateAttachments(txCtx, entity.ID, dto.Attachments); err != nil {
			return err
		}
		// 附件在同一事务里读取，省掉提交后的一次往返，也保证读到刚写入的关联
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
		return nil, errs.NewBizError("单据 ID 为空")
	}

	entity := dto.ToEntity()
	if err := s.ValidateAmount(entity); err != nil {
		return nil, err
	}

	var attaches []attach.Attach
	err := s.billRepo.Transaction(ctx, func(txCtx context.Context) error {
		// 只取状态：本次更新与状态流转互斥由数据库行锁 + 乐观锁版本号保证，
		// 不需要把明细也读出来
		bill, err := s.billRepo.FindByID(txCtx, entity.ID)
		if err != nil {
			return err
		}
		if bill == nil {
			return errs.NewBizError("单据不存在")
		}
		entity.State = bill.State
		if err = s.billRepo.Updates(txCtx, entity); err != nil {
			return err
		}
		if err = s.relSrv.UpdateAttachments(txCtx, entity.ID, dto.Attachments); err != nil {
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

// Delete 只允许删除创建状态的单据，逐项返回结果。
func (s *Service) Delete(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.billRepo.Transaction(ctx, func(txCtx context.Context) error {
			bill, err := s.billRepo.FindByID(txCtx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if bill.State != Created {
				return errs.NewBizError("非创建状态不能删除")
			}
			// 先断开关联（附件模块负责删除文件），再删主表；明细由外键级联删除的语义
			// 在 Delete 里显式执行
			if err = s.relSrv.UpdateAttachments(txCtx, bill.ID, nil); err != nil {
				return err
			}
			deleted, err := s.billRepo.Delete(txCtx, bill.ID)
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

// transition 状态流转的公共骨架
//
// 每次流转都是「读当前状态 → 校验 → 只更新状态相关列」，不再回写整行：
// 整行回写会把并发编辑的其它字段用旧快照覆盖掉，而这里只写 state 与日期列。
func (s *Service) transition(
	ctx context.Context,
	ids []uint,
	label string,
	apply func(bill *ServiceBill) error,
) (*result.ActionsResult[uint, any], error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 为空")
	}
	return result.ExecuteActions(ids, func(id uint) (any, error) {
		err := s.billRepo.Transaction(ctx, func(txCtx context.Context) error {
			bill, err := s.billRepo.FindByID(txCtx, id)
			if err != nil {
				return err
			}
			if bill == nil {
				return errs.NewBizError("单据不存在")
			}
			if err = apply(bill); err != nil {
				return err
			}
			log.Infof("%s %v 状态变更为 %v", label, bill.Number, bill.State)
			return s.billRepo.UpdateState(txCtx, bill)
		})
		return nil, err
	}), nil
}

func (s *Service) Process(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	return s.transition(ctx, ids, "服务单", func(bill *ServiceBill) error {
		if bill.State != Created {
			return errs.NewBizError("非创建状态的单据不能处理")
		}
		bill.State = Processing
		return nil
	})
}

func (s *Service) Processed(ctx context.Context, ids []uint, processedDate *time.Time) (*result.ActionsResult[uint, any], error) {
	if processedDate == nil {
		processedDate = new(time.Now())
	}
	return s.transition(ctx, ids, "服务单", func(bill *ServiceBill) error {
		if bill.State != Processing {
			return errs.NewBizError("非处理中状态的单据不能处理完成")
		}
		bill.State = Processed
		bill.ProcessedDate = processedDate
		return nil
	})
}

func (s *Service) Finish(ctx context.Context, ids []uint, finishedDate *time.Time) (*result.ActionsResult[uint, any], error) {
	if finishedDate == nil {
		finishedDate = new(time.Now())
	}
	return s.transition(ctx, ids, "服务单", func(bill *ServiceBill) error {
		if bill.State != Processed {
			return errs.NewBizError("非处理完成状态的单据不能完成")
		}
		bill.State = Finished
		bill.FinishedDate = finishedDate
		return nil
	})
}

func (s *Service) CancelProcess(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	return s.transition(ctx, ids, "服务单", func(bill *ServiceBill) error {
		if bill.State != Processing {
			return errs.NewBizError("非处理中状态的单据不能取消处理")
		}
		bill.State = Created
		bill.ProcessedDate = nil
		return nil
	})
}

func (s *Service) CancelProcessed(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	return s.transition(ctx, ids, "服务单", func(bill *ServiceBill) error {
		if bill.State != Processed {
			return errs.NewBizError("非处理完成状态的单据不能取消处理完成")
		}
		bill.State = Processing
		bill.ProcessedDate = nil
		return nil
	})
}

func (s *Service) CancelFinish(ctx context.Context, ids []uint) (*result.ActionsResult[uint, any], error) {
	return s.transition(ctx, ids, "服务单", func(bill *ServiceBill) error {
		if bill.State != Finished {
			return errs.NewBizError("非完成状态的单据不能取消完成")
		}
		bill.State = Processed
		bill.FinishedDate = nil
		return nil
	})
}

// GenerateByFile 上传并解析文件生成单据 DTO，不落库。
func (s *Service) GenerateByFile(ctx context.Context, file *multipart.FileHeader) (*DTO, error) {
	attaches, err := s.attachSrv.Upload(ctx, AttachNamespace, []*multipart.FileHeader{file})
	if err != nil {
		return nil, err
	}
	attachment := attaches[0]
	bill, err := s.mapper.Map(&attachment)
	if err != nil {
		return nil, err
	}
	bill.Attachments = attaches
	return bill, nil
}

func (s *Service) Export(ctx context.Context, ids []uint) (string, error) {
	if len(ids) == 0 {
		return "", errs.NewBizError("ID 不能为空")
	}

	// 一次取回主表 + 明细 + 公司（固定 3 条语句），而不是每张单据再查一次
	bills, err := s.billRepo.FindAllWithDetails(ctx, ids)
	if err != nil {
		return "", err
	}
	if len(bills) == 0 {
		return "", errs.NewBizError("单据不存在")
	}

	// 一次取回全部单据的附件，避免逐单据查关联
	rels, err := s.relSrv.FindByBillIDs(ctx, ids)
	if err != nil {
		return "", err
	}

	tempDir, err := s.attachSrv.CreateTempDir("export")
	if err != nil {
		return "", err
	}

	ops := make([]attach.FileOp, 0, len(rels))
	rows := make([][]string, 0, len(bills)+2)

	rows = append(rows, []string{"单据编号", "状态", "项目名称", "项目地址", "总额", "安装完成日期", "备注"})
	var totalAmount float64

	for _, bill := range bills {
		var detailStr strings.Builder
		for _, detail := range bill.Details {
			detailStr.WriteString(fmt.Sprintf("%s : %.2f * %.2f ; ", detail.Device, detail.UnitPrice, detail.Quantity))
		}
		processDateStr := ""
		if bill.ProcessedDate != nil {
			processDateStr = bill.ProcessedDate.Format("2006-01-02")
		}
		rows = append(rows, []string{
			bill.Number,
			bill.State.String(),
			bill.ProjectName,
			bill.ProjectAddress,
			fmt.Sprintf("%.2f", bill.TotalAmount),
			processDateStr,
			detailStr.String(),
		})

		totalAmount += bill.TotalAmount
	}

	for _, bill := range bills {
		for _, attachment := range rels[bill.ID] {
			originPath, err := s.attachSrv.GetAbsolutePathByRela(attachment.RelativePath)
			if err != nil {
				return "", err
			}
			ops = append(ops, attach.FileOp{
				Type:   attach.FileOpCopy,
				Origin: originPath,
				Target: uniqueTargetPath(filepath.Join(tempDir, bill.Number), attachment.Name),
			})
		}
	}

	rows = append(rows, []string{"", "合计", fmt.Sprintf("%.2f", totalAmount), "", ""})

	excelPath := filepath.Join(tempDir, fmt.Sprintf("导出结果%s.xlsx", time.Now().Format("20060102150405")))
	if err = attach.GenerateExcelFromList(rows, excelPath); err != nil {
		return "", errs.NewBizError("生成Excel失败: " + err.Error())
	}

	if err = attach.Exec(ops); err != nil {
		return "", errs.NewBizError("文件操作失败: " + err.Error())
	}
	zipPath, err := attach.Zip(tempDir, "")
	if err != nil {
		return "", err
	}
	return zipPath, nil
}

// uniqueTargetPath 处理导出目录下的同名附件：name、1-name、2-name ……
func uniqueTargetPath(dir, name string) string {
	targetPath := filepath.Join(dir, name)
	repeatCount := 1
	for attach.Exists(targetPath) {
		targetPath = fmt.Sprintf("%s/%d-%s", dir, repeatCount, name)
		repeatCount++
	}
	return targetPath
}
