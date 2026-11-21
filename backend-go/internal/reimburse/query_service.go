package reimburse

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
)

// QueryService 报销单查询与导出读取；动作服务通过 RequireByID 复用同一套访问入口。
type QueryService struct {
	reimburseRepo *Repository
	relSrv        *AttachRelService
}

func newQueryService(reimburseRepo *Repository, relSrv *AttachRelService) *QueryService {
	return &QueryService{
		reimburseRepo: reimburseRepo,
		relSrv:        relSrv,
	}
}

func (s *QueryService) FindByID(ctx context.Context, id uint) (*DTO, error) {
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

func (s *QueryService) FindByParam(ctx context.Context, param *QueryParam) (*result.PageResult[DTO], error) {
	if param == nil {
		return nil, errs.NewBizError("查询参数为空")
	}
	res, err := s.reimburseRepo.FindByParam(ctx, param)
	if err != nil {
		return nil, err
	}
	return result.NewDTOPageResult(res, ToBaseDTOs), nil
}

// RequireByID 动作服务按 id 读写前使用：不存在时返回业务错误。
func (s *QueryService) RequireByID(ctx context.Context, id uint) (*Reimburse, error) {
	bill, err := s.reimburseRepo.FindByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if bill == nil {
		return nil, errs.NewBizError("单据不存在")
	}
	return bill, nil
}

// FindAllWithDetails 导出读取：主表与明细按两条语句批量取回。
func (s *QueryService) FindAllWithDetails(ctx context.Context, ids []uint) ([]Reimburse, error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 不能为空")
	}
	bills, err := s.reimburseRepo.FindAllWithDetails(ctx, ids)
	if err != nil {
		return nil, err
	}
	if len(bills) == 0 {
		return nil, errs.NewBizError("单据不存在")
	}
	return bills, nil
}
