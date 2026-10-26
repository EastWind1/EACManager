package security

import (
	"backend-go/pkg/auth"
	appContext "backend-go/pkg/context"
	"backend-go/pkg/errs"
	"context"
	"regexp"
	"slices"
	"strings"
)

var roleCodePattern = regexp.MustCompile(`^[A-Z][A-Z0-9_]*$`)

// Service 角色、权限与用户角色服务
type Service struct {
	repo *Repository
}

// NewService 创建安全服务
func NewService(ctx *appContext.AppContext) *Service {
	return &Service{repo: NewRepository(ctx.Db)}
}

// ---------------------------------------------------------------------------
// 权限目录
// ---------------------------------------------------------------------------

// Permissions 获取全部可配置权限
func (s *Service) Permissions(ctx context.Context) (map[string][]PermissionDTO, error) {
	permissions, err := s.repo.FindAllTypesOrdered(ctx)
	if err != nil {
		return nil, err
	}
	res := make(map[string][]PermissionDTO)
	for _, permission := range permissions {
		res[permission.Module] = append(res[permission.Module], *permission.ToDTO())
	}
	return res, nil
}

// HasAdminUser 是否存在管理员用户
func (s *Service) HasAdminUser(ctx context.Context) (bool, error) {
	return s.repo.HasAdminUser(ctx, AdminRoleCode)
}

// PermissionCodes 查询用户权限编码
func (s *Service) PermissionCodes(ctx context.Context, userID uint) ([]string, error) {
	return s.repo.FindPermissionsByUserID(ctx, userID)
}

// HasPermission 判断当前用户是否拥有权限
func (s *Service) HasPermission(ctx context.Context, code string) (bool, error) {
	user, err := auth.GetCurrentUser(ctx)
	if err != nil {
		return false, err
	}
	permissions, err := s.PermissionCodes(ctx, user.GetID())
	if err != nil {
		return false, err
	}
	return slices.Contains(permissions, code), nil
}

// ---------------------------------------------------------------------------
// 角色管理
// ---------------------------------------------------------------------------

// Roles 查询全部角色
func (s *Service) Roles(ctx context.Context) ([]RoleDTO, error) {
	roles, err := s.repo.FindAllRoles(ctx)
	if err != nil {
		return nil, err
	}
	res := make([]RoleDTO, 0, len(roles))
	for _, role := range roles {
		res = append(res, *role.ToDTO())
	}
	return res, nil
}

// CreateRole 创建角色
func (s *Service) CreateRole(ctx context.Context, dto *RoleDTO) (*RoleDTO, error) {
	if !roleCodePattern.MatchString(dto.Code) {
		return nil, errs.NewBizError("角色编码必须为大写字母、数字或下划线，且不能以数字开头")
	}
	if strings.HasPrefix(dto.Code, "ROLE_") {
		return nil, errs.NewBizError("角色编码不需要 ROLE_ 前缀")
	}
	if strings.TrimSpace(dto.Name) == "" {
		return nil, errs.NewBizError("角色名称不能为空")
	}
	existing, err := s.repo.FindRoleByCode(ctx, dto.Code)
	if err != nil {
		return nil, err
	}
	if existing != nil {
		return nil, errs.NewBizError("角色编码已存在")
	}
	role := Role{Code: dto.Code, Name: dto.Name, SortOrder: dto.SortOrder, Disabled: false}
	if err := s.repo.CreateRole(ctx, &role); err != nil {
		return nil, err
	}
	res := role.ToDTO()
	return res, nil
}

// UpdateRole 修改角色名称/排序/禁用状态
func (s *Service) UpdateRole(ctx context.Context, dto *RoleDTO) (*RoleDTO, error) {
	if dto.ID == 0 {
		return nil, errs.NewBizError("角色 id 不能为空")
	}
	role, err := s.repo.FindRoleByID(ctx, dto.ID)
	if err != nil {
		return nil, err
	}
	if role == nil {
		return nil, errs.NewBizError("角色不存在")
	}
	if role.Code == AdminRoleCode && dto.Disabled {
		return nil, errs.NewBizError("管理员角色不能禁用")
	}
	if strings.TrimSpace(dto.Name) == "" {
		return nil, errs.NewBizError("角色名称不能为空")
	}
	role.Name = dto.Name
	role.SortOrder = dto.SortOrder
	role.Disabled = dto.Disabled
	if err := s.repo.UpdateRole(ctx, role); err != nil {
		return nil, err
	}
	res := *role.ToDTO()
	return &res, nil
}

// DeleteRole 删除角色
func (s *Service) DeleteRole(ctx context.Context, id uint) error {
	role, err := s.repo.FindRoleByID(ctx, id)
	if err != nil {
		return err
	}
	if role == nil {
		return errs.NewBizError("角色不存在")
	}
	if role.Code == AdminRoleCode {
		return errs.NewBizError("管理员角色不能删除")
	}
	return s.repo.Transaction(ctx, func(txCtx context.Context) error {
		if err := s.repo.DeleteUserRolesByRoleID(txCtx, id); err != nil {
			return err
		}
		if err := s.repo.DeleteRolePermissionsByRoleID(txCtx, id); err != nil {
			return err
		}
		return s.repo.DeleteRole(txCtx, id)
	})
}

// ---------------------------------------------------------------------------
// 用户角色与权限
// ---------------------------------------------------------------------------

// RoleCodes 查询用户角色编码
func (s *Service) RoleCodes(ctx context.Context, userID uint) ([]string, error) {
	return s.repo.FindRoleCodesByUserID(ctx, userID)
}

// UserRoles 查询用户拥有的角色
func (s *Service) UserRoles(ctx context.Context, userID uint) ([]RoleDTO, error) {
	codes, err := s.repo.FindRoleCodesByUserID(ctx, userID)
	if err != nil {
		return nil, err
	}
	if len(codes) == 0 {
		return []RoleDTO{}, nil
	}

	roles, err := s.repo.FindRolesByCodes(ctx, codes)
	if err != nil {
		return nil, err
	}
	res := make([]RoleDTO, 0, len(roles))
	for _, role := range roles {
		res = append(res, *role.ToDTO())
	}
	return res, nil
}

// UserPermissions 查询用户的权限
func (s *Service) UserPermissions(ctx context.Context, userID uint) ([]PermissionDTO, error) {
	codes, err := s.PermissionCodes(ctx, userID)
	if err != nil {
		return nil, err
	}
	if len(codes) == 0 {
		return []PermissionDTO{}, nil
	}
	permissions, err := s.Permissions(ctx)
	if err != nil {
		return nil, err
	}
	return resolvePermissions(codes, permissionDTOMap(permissions)), nil
}

// SetUserRoles 设置用户角色（整体替换）
func (s *Service) SetUserRoles(ctx context.Context, userID uint, roleCodes []string) error {
	uniqueCodes := unique(roleCodes)
	return s.repo.Transaction(ctx, func(txCtx context.Context) error {
		if err := s.repo.DeleteUserRolesByUserID(txCtx, userID); err != nil {
			return err
		}
		if len(uniqueCodes) == 0 {
			return nil
		}
		roles, err := s.repo.FindRolesByCodes(txCtx, uniqueCodes)
		if err != nil {
			return err
		}
		if len(roles) != len(uniqueCodes) {
			return errs.NewBizError("存在未知角色")
		}
		for _, role := range roles {
			if err := s.repo.CreateUserRole(txCtx, &UserRole{UserID: userID, RoleID: role.ID}); err != nil {
				return err
			}
		}
		return nil
	})
}

// HasRole 判断用户是否拥有角色
func (s *Service) HasRole(ctx context.Context, userID uint, roleCode string) (bool, error) {
	codes, err := s.RoleCodes(ctx, userID)
	if err != nil {
		return false, err
	}
	return slices.Contains(codes, roleCode), nil
}

// ---------------------------------------------------------------------------
// 角色权限配置
// ---------------------------------------------------------------------------

// RolePermissions 查询角色权限
func (s *Service) RolePermissions(ctx context.Context, roleID uint) ([]PermissionDTO, error) {
	role, err := s.repo.FindRoleByID(ctx, roleID)
	if err != nil {
		return nil, err
	}
	if role == nil {
		return nil, errs.NewBizError("角色不存在")
	}
	codes, err := s.repo.FindPermissionsByRoleID(ctx, roleID)
	if err != nil {
		return nil, err
	}
	if len(codes) == 0 {
		return []PermissionDTO{}, nil
	}
	permissions, err := s.Permissions(ctx)
	if err != nil {
		return nil, err
	}
	return resolvePermissions(codes, permissionDTOMap(permissions)), nil
}

// UpdateRolePermissions 更新角色权限
func (s *Service) UpdateRolePermissions(ctx context.Context, roleID uint, permissionIDs []uint) ([]PermissionDTO, error) {
	role, err := s.repo.FindRoleByID(ctx, roleID)
	if err != nil {
		return nil, err
	}
	if role == nil {
		return nil, errs.NewBizError("角色不存在")
	}

	requested := uniqueUint(permissionIDs)
	if len(requested) > 0 {
		existing, err := s.repo.CountPermissionsByIDs(ctx, requested)
		if err != nil {
			return nil, err
		}
		if int(existing) != len(requested) {
			return nil, errs.NewBizError("存在未知权限")
		}
	}

	err = s.repo.Transaction(ctx, func(txCtx context.Context) error {
		if err := s.repo.DeleteRolePermissionsByRoleID(txCtx, roleID); err != nil {
			return err
		}
		for _, permissionID := range requested {
			if err := s.repo.CreateRolePermission(txCtx, &RolePermission{
				RoleID:       roleID,
				PermissionID: permissionID,
			}); err != nil {
				return err
			}
		}
		if role.Code == AdminRoleCode {
			codes, err := s.repo.FindPermissionsByRoleID(txCtx, roleID)
			if err != nil {
				return err
			}
			if !slices.Contains(codes, QueryAuthCode) ||
				!slices.Contains(codes, UpdateAuthCode) {
				return errs.NewBizError("管理员角色必须保留权限配置的查看和修改权限")
			}
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	return s.RolePermissions(ctx, roleID)
}

// ---------------------------------------------------------------------------
// 通用
// ---------------------------------------------------------------------------

func permissionDTOMap(groups map[string][]PermissionDTO) map[string]PermissionDTO {
	res := make(map[string]PermissionDTO)
	for _, permissions := range groups {
		for _, permission := range permissions {
			res[permission.Code] = permission
		}
	}
	return res
}

func resolvePermissions(codes []string, byCode map[string]PermissionDTO) []PermissionDTO {
	seen := make(map[string]struct{}, len(codes))
	res := make([]PermissionDTO, 0, len(codes))
	for _, code := range codes {
		if _, ok := seen[code]; ok {
			continue
		}
		permission, ok := byCode[code]
		if !ok {
			continue
		}
		seen[code] = struct{}{}
		res = append(res, permission)
	}
	return res
}

func uniqueUint(values []uint) []uint {
	seen := make(map[uint]struct{}, len(values))
	res := make([]uint, 0, len(values))
	for _, value := range values {
		if _, ok := seen[value]; ok {
			continue
		}
		seen[value] = struct{}{}
		res = append(res, value)
	}
	return res
}

func unique(values []string) []string {
	seen := make(map[string]struct{}, len(values))
	res := make([]string, 0, len(values))
	for _, value := range values {
		if _, ok := seen[value]; ok {
			continue
		}
		seen[value] = struct{}{}
		res = append(res, value)
	}
	return res
}
