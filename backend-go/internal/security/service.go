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

type Service struct {
	repo *Repository
}

func NewService(ctx *appContext.AppContext) *Service {
	return &Service{repo: NewRepository(ctx.Db)}
}

// Permissions 按模块分组返回权限目录。
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

func (s *Service) HasAdminUser(ctx context.Context) (bool, error) {
	return s.repo.HasAdminUser(ctx, AdminRoleCode)
}

// RoleByCodeForUpdate 按编码取角色并加行锁，未查到时返回 nil
//
// 供首管理员初始化使用：调用方需在事务内调用，行锁才有意义。
func (s *Service) RoleByCodeForUpdate(ctx context.Context, code string) (*Role, error) {
	return s.repo.FindRoleByCodeForUpdate(ctx, code)
}

func (s *Service) PermissionCodes(ctx context.Context, userID uint) ([]string, error) {
	return s.repo.FindPermissionsByUserID(ctx, userID)
}

// HasPermission 保留给没有 fiber.Ctx 的调用方；中间件与处理器应优先用 middleware.go 的请求级缓存版本。
func (s *Service) HasPermission(ctx context.Context, code string) (bool, error) {
	codes, err := s.currentUserPermissionCodes(ctx)
	if err != nil {
		return false, err
	}
	return slices.Contains(codes, code), nil
}

func (s *Service) currentUserPermissionCodes(ctx context.Context) ([]string, error) {
	user, err := auth.GetCurrentUser(ctx)
	if err != nil {
		return nil, err
	}
	return s.PermissionCodes(ctx, user.GetID())
}

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
	role := Role{Code: dto.Code, Name: dto.Name, SortOrder: dto.SortOrder, Disabled: false}
	// 不再先查后插：编码上的唯一索引本身就是判重，省掉一次往返，也消除并发下的竞态
	if err := s.repo.CreateRole(ctx, &role); err != nil {
		return nil, err
	}
	return role.ToDTO(), nil
}

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
	return role.ToDTO(), nil
}

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
		// 两张关联表都是批量 DELETE，不会先逐条查出来
		if err := s.repo.DeleteUserRolesByRoleID(txCtx, id); err != nil {
			return err
		}
		if err := s.repo.DeleteRolePermissionsByRoleID(txCtx, id); err != nil {
			return err
		}
		return s.repo.DeleteRole(txCtx, id)
	})
}

func (s *Service) RoleCodes(ctx context.Context, userID uint) ([]string, error) {
	return s.repo.FindRoleCodesByUserID(ctx, userID)
}

func (s *Service) UserRoles(ctx context.Context, userID uint) ([]RoleDTO, error) {
	roles, err := s.repo.FindEnabledRolesByUserID(ctx, userID)
	if err != nil {
		return nil, err
	}
	res := make([]RoleDTO, 0, len(roles))
	for _, role := range roles {
		res = append(res, *role.ToDTO())
	}
	return res, nil
}

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

// SetUserRoles 整体替换用户的角色，返回写入后的角色列表。
func (s *Service) SetUserRoles(ctx context.Context, userID uint, roleCodes []string) ([]RoleDTO, error) {
	uniqueCodes := unique(roleCodes)
	granted := make([]RoleDTO, 0, len(uniqueCodes))
	err := s.repo.Transaction(ctx, func(txCtx context.Context) error {
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
		// 一次批量插入，而不是逐角色往返
		relations := make([]UserRole, 0, len(roles))
		for _, role := range roles {
			relations = append(relations, UserRole{UserID: userID, RoleID: role.ID})
			granted = append(granted, *role.ToDTO())
		}
		return s.repo.CreateUserRoles(txCtx, relations)
	})
	if err != nil {
		return nil, err
	}
	return granted, nil
}

func (s *Service) HasRole(ctx context.Context, userID uint, roleCode string) (bool, error) {
	codes, err := s.RoleCodes(ctx, userID)
	if err != nil {
		return false, err
	}
	return slices.Contains(codes, roleCode), nil
}

func (s *Service) RolePermissions(ctx context.Context, roleID uint) ([]PermissionDTO, error) {
	role, err := s.repo.FindRoleByID(ctx, roleID)
	if err != nil {
		return nil, err
	}
	if role == nil {
		return nil, errs.NewBizError("角色不存在")
	}
	permissions, err := s.repo.FindPermissionsByRoleID(ctx, roleID)
	if err != nil {
		return nil, err
	}
	return permissionDTOs(permissions), nil
}

func (s *Service) UpdateRolePermissions(ctx context.Context, roleID uint, permissionIDs []uint) ([]PermissionDTO, error) {
	role, err := s.repo.FindRoleByID(ctx, roleID)
	if err != nil {
		return nil, err
	}
	if role == nil {
		return nil, errs.NewBizError("角色不存在")
	}

	requested := uniqueUint(permissionIDs)
	permissions := make([]PermissionDTO, 0, len(requested))
	err = s.repo.Transaction(ctx, func(txCtx context.Context) error {
		if len(requested) > 0 {
			found, err := s.repo.FindPermissionsByIDs(txCtx, requested)
			if err != nil {
				return err
			}
			if len(found) != len(requested) {
				return errs.NewBizError("存在未知权限")
			}
			permissions = permissionDTOs(found)
		}
		if err := s.repo.DeleteRolePermissionsByRoleID(txCtx, roleID); err != nil {
			return err
		}
		relations := make([]RolePermission, 0, len(requested))
		for _, permissionID := range requested {
			relations = append(relations, RolePermission{
				RoleID:       roleID,
				PermissionID: permissionID,
			})
		}
		// 一次批量插入
		if err := s.repo.CreateRolePermissions(txCtx, relations); err != nil {
			return err
		}
		// 管理员角色必须保留权限配置的查看与修改，校验基于「将要写入的集合」而不是再查一次库
		if role.Code == AdminRoleCode {
			codes := make([]string, 0, len(permissions))
			for _, permission := range permissions {
				codes = append(codes, permission.Code)
			}
			if !slices.Contains(codes, QueryAuthCode) || !slices.Contains(codes, UpdateAuthCode) {
				return errs.NewBizError("管理员角色必须保留权限配置的查看和修改权限")
			}
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	if permissions == nil {
		return []PermissionDTO{}, nil
	}
	return permissions, nil
}

func permissionDTOs(permissions []Permission) []PermissionDTO {
	res := make([]PermissionDTO, 0, len(permissions))
	for _, permission := range permissions {
		res = append(res, *permission.ToDTO())
	}
	return res
}

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
