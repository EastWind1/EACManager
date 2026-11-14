package security

import (
	"backend-go/pkg/errs"
	"context"
	"slices"

	"github.com/gofiber/fiber/v3/log"
)

// PermissionSyncService 权限同步服务，用于各模块注册权限字段
type PermissionSyncService struct {
	repo        *AuthRepository
	definitions []PermissionDTO
}

func newPermissionSyncService(repo *AuthRepository) *PermissionSyncService {
	return &PermissionSyncService{repo: repo}
}

func (s *PermissionSyncService) Register(definitions ...PermissionDTO) {
	s.definitions = append(s.definitions, definitions...)
}

// 将各模块声明的权限同步到 sys_permission（upsert）
func (s *PermissionSyncService) syncPermission() error {
	if len(s.definitions) == 0 {
		log.Warn("未发现任何模块权限定义")
		return nil
	}

	declared := make(map[string]PermissionDTO, len(s.definitions))
	for _, definition := range s.definitions {
		if _, ok := declared[definition.Code]; ok {
			return errs.NewBizError("重复的权限编码: " + definition.Code)
		}
		declared[definition.Code] = definition
	}

	ctx := SetUserToCtx(context.Background(), &AuthorityUser{Username: "system"})
	return s.repo.Transaction(ctx, func(txCtx context.Context) error {
		existing, err := s.repo.FindAllTypesOrdered(txCtx)
		if err != nil {
			return err
		}
		byCode := make(map[string]*Permission, len(existing))
		for i := range existing {
			byCode[existing[i].Code] = &existing[i]
		}

		inserted, updated := 0, 0
		for _, definition := range s.definitions {
			permission, ok := byCode[definition.Code]
			if !ok {
				entity := Permission{
					Code:       definition.Code,
					Module:     definition.Module,
					ModuleName: definition.ModuleName,
					Name:       definition.Name,
					SortOrder:  definition.SortOrder,
				}
				if err := s.repo.CreatePermission(txCtx, &entity); err != nil {
					return err
				}
				inserted++
				continue
			}
			permission.Module = definition.Module
			permission.ModuleName = definition.ModuleName
			permission.Name = definition.Name
			permission.SortOrder = definition.SortOrder
			if err := s.repo.UpdatePermission(txCtx, permission); err != nil {
				return err
			}
			updated++
		}
		log.Infof("权限类型同步完成，新增 %d 个，更新 %d 个", inserted, updated)
		return nil
	})
}

// InitAdmin 确保管理员、普通用户角色存在，并为管理员补齐全部权限
func (s *PermissionSyncService) initAdmin() error {
	ctx := SetUserToCtx(context.Background(), &AuthorityUser{Username: "system"})
	return s.repo.Transaction(ctx, func(txCtx context.Context) error {
		admin, err := s.ensureRole(txCtx, AdminRoleCode, "管理员", 1)
		if err != nil {
			return err
		}
		if _, err := s.ensureRole(txCtx, "USER", "普通用户", 2); err != nil {
			return err
		}

		existing, err := s.repo.FindPermissionCodesByRoleID(txCtx, admin.ID)
		if err != nil {
			return err
		}
		permissions, err := s.repo.FindAllTypesOrdered(txCtx)
		if err != nil {
			return err
		}
		added := 0
		for _, permission := range permissions {
			if slices.Contains(existing, permission.Code) {
				continue
			}
			if err := s.repo.CreateRolePermission(txCtx, &RolePermission{
				RoleID:       admin.ID,
				PermissionID: permission.ID,
			}); err != nil {
				return err
			}
			added++
		}
		if added > 0 {
			log.Infof("管理员角色权限不完整，已补齐 %d 项权限", added)
		}
		return nil
	})
}

// Sync 同步各模块权限配置至服务器，必要时初始化管理员角色
func (s *PermissionSyncService) Sync() (err error) {
	err = s.syncPermission()
	if err != nil {
		return err
	}
	err = s.initAdmin()
	return
}

func (s *PermissionSyncService) ensureRole(ctx context.Context, code, name string, sortOrder int) (*Role, error) {
	role, err := s.repo.FindRoleByCode(ctx, code)
	if err != nil {
		return nil, err
	}
	if role != nil {
		return role, nil
	}
	role = &Role{Code: code, Name: name, SortOrder: sortOrder}
	if err := s.repo.CreateRole(ctx, role); err != nil {
		return nil, err
	}
	log.Infof("角色不存在，已创建: %s", code)
	return role, nil
}
