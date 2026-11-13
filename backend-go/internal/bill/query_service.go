package bill

import (
	"backend-go/internal/security"
	"backend-go/internal/user"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
)

// QueryService 服务单查询服务。
//
// 列表、详情、导出读取、负责人候选以及数据权限都收在这里；动作服务只负责写，
// 不再单独维护 dataScope。按 id 写入前也通过 RequireByID 复用同一套权限判断。
type QueryService struct {
	billRepo    *Repository
	relSrv      *RelService
	userSrv     *user.Service
	securitySrv *security.Service
}

func newQueryService(billRepo *Repository, relSrv *RelService, userSrv *user.Service, securitySrv *security.Service) *QueryService {
	return &QueryService{
		billRepo:    billRepo,
		relSrv:      relSrv,
		userSrv:     userSrv,
		securitySrv: securitySrv,
	}
}

// Scope 返回数据权限范围；nil 表示可查看全部，否则只能访问指派给该用户的单据。
func (s *QueryService) Scope(ctx context.Context) (*uint, error) {
	currentUser, err := security.GetCurrentUser(ctx)
	if err != nil {
		return nil, err
	}
	canAll, err := s.securitySrv.HasPermission(ctx, DataAllAuthCode)
	if err != nil {
		return nil, err
	}
	if canAll {
		return nil, nil
	}
	id := currentUser.GetID()
	return &id, nil
}

func (s *QueryService) CurrentUserID(ctx context.Context) (uint, error) {
	currentUser, err := security.GetCurrentUser(ctx)
	if err != nil {
		return 0, err
	}
	return currentUser.GetID(), nil
}

func (s *QueryService) CanAssign(ctx context.Context) (bool, error) {
	return s.securitySrv.HasPermission(ctx, AssignAuthCode)
}

func (s *QueryService) CheckAccess(ctx context.Context, scope *uint, billID uint) error {
	if scope == nil {
		return nil
	}
	assigned, err := s.billRepo.IsAssignedTo(ctx, billID, *scope)
	if err != nil {
		return err
	}
	if !assigned {
		return errs.NewBizError("无权访问该服务单")
	}
	return nil
}

// RequireByID 动作服务按 id 读写前使用：加载实体并校验当前用户的数据权限。
func (s *QueryService) RequireByID(ctx context.Context, id uint) (*ServiceBill, error) {
	if id == 0 {
		return nil, errs.NewBizError("ID不能为空")
	}
	bill, err := s.billRepo.FindByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if bill == nil {
		return nil, errs.NewBizError("单据不存在")
	}
	scope, err := s.Scope(ctx)
	if err != nil {
		return nil, err
	}
	if err = s.CheckAccess(ctx, scope, bill.ID); err != nil {
		return nil, err
	}
	return bill, nil
}

func (s *QueryService) FindByID(ctx context.Context, id uint) (*DTO, error) {
	if id == 0 {
		return nil, errs.NewBizError("ID不能为空")
	}
	scope, err := s.Scope(ctx)
	if err != nil {
		return nil, err
	}
	bill, err := s.billRepo.FindFullById(ctx, id)
	if err != nil {
		return nil, err
	}
	if bill == nil {
		return nil, errs.NewBizError("单据不存在")
	}
	if err = s.CheckAccess(ctx, scope, bill.ID); err != nil {
		return nil, err
	}
	attaches, err := s.relSrv.FindAttachments(ctx, bill.ID)
	if err != nil {
		return nil, err
	}
	dto := bill.ToDTO(attaches)
	dto.Assignees, err = s.FindAssignees(ctx, bill.ID)
	if err != nil {
		return nil, err
	}
	return dto, nil
}

func (s *QueryService) FindByParam(ctx context.Context, param *QueryParam) (*result.PageResult[DTO], error) {
	if param == nil {
		return nil, errs.NewBizError("查询参数为空")
	}
	scope, err := s.Scope(ctx)
	if err != nil {
		return nil, err
	}
	res, err := s.billRepo.FindByParam(ctx, param, scope)
	if err != nil {
		return nil, err
	}
	page := result.NewDTOPageResult(res, ToBaseDTOs)
	if err = s.fillAssignees(ctx, page.Items); err != nil {
		return nil, err
	}
	return page, nil
}

// FindAllForExport 导出读取；调用方拿到实体后即可直接生成文件，不再单独判断数据权限。
func (s *QueryService) FindAllForExport(ctx context.Context, ids []uint) ([]ServiceBill, error) {
	if len(ids) == 0 {
		return nil, errs.NewBizError("ID 不能为空")
	}
	scope, err := s.Scope(ctx)
	if err != nil {
		return nil, err
	}
	bills, err := s.billRepo.FindAllWithDetails(ctx, ids, scope)
	if err != nil {
		return nil, err
	}
	uniqueIDs := make(map[uint]struct{}, len(ids))
	for _, id := range ids {
		uniqueIDs[id] = struct{}{}
	}
	if len(bills) == 0 || len(bills) != len(uniqueIDs) {
		return nil, errs.NewBizError("单据不存在或无权访问")
	}
	return bills, nil
}

// FindAssignees 返回启用用户，供指派下拉框使用，不要求用户管理权限。
func (s *QueryService) FindAssignees(ctx context.Context, billID uint) ([]AssigneeDTO, error) {
	assignees, err := s.loadAssignees(ctx, []uint{billID})
	if err != nil {
		return nil, err
	}
	return assignees[billID], nil
}

func (s *QueryService) fillAssignees(ctx context.Context, bills []DTO) error {
	if len(bills) == 0 {
		return nil
	}
	billIDs := make([]uint, 0, len(bills))
	for i := range bills {
		billIDs = append(billIDs, bills[i].ID)
	}
	assignees, err := s.loadAssignees(ctx, billIDs)
	if err != nil {
		return err
	}
	for i := range bills {
		bills[i].Assignees = assignees[bills[i].ID]
	}
	return nil
}

func (s *QueryService) loadAssignees(ctx context.Context, billIDs []uint) (map[uint][]AssigneeDTO, error) {
	res := make(map[uint][]AssigneeDTO, len(billIDs))
	rows, err := s.billRepo.FindAssigneesByBillIDs(ctx, billIDs)
	if err != nil {
		return nil, err
	}
	if len(rows) == 0 {
		return res, nil
	}
	userIDs := make([]uint, 0, len(rows))
	for _, row := range rows {
		userIDs = append(userIDs, row.UserID)
	}
	users, err := s.userSrv.FindByIDs(ctx, userIDs)
	if err != nil {
		return nil, err
	}
	usersByID := make(map[uint]AssigneeDTO, len(users))
	for _, u := range users {
		usersByID[u.ID] = AssigneeDTO{ID: u.ID, Username: u.Username, Name: u.Name}
	}
	for _, row := range rows {
		if assigneeDTO, ok := usersByID[row.UserID]; ok {
			res[row.ServiceBillID] = append(res[row.ServiceBillID], assigneeDTO)
		}
	}
	return res, nil
}

func (s *QueryService) ListAssignees(ctx context.Context) ([]AssigneeDTO, error) {
	users, err := s.userSrv.FindEnabled(ctx)
	if err != nil {
		return nil, err
	}
	res := make([]AssigneeDTO, 0, len(users))
	for _, u := range users {
		res = append(res, AssigneeDTO{ID: u.ID, Username: u.Username, Name: u.Name})
	}
	return res, nil
}
