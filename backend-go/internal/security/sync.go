package security

import (
	"backend-go/pkg/auth"
	appContext "backend-go/pkg/context"
	"backend-go/pkg/errs"
	"context"
	"slices"

	"github.com/gofiber/fiber/v3/log"
)

// PermissionSyncService 启动时同步各模块声明的权限
type PermissionSyncService struct {
	repo        *Repository
	definitions []PermissionDTO
}

// NewPermissionSyncService 创建权限同步服务
func NewPermissionSyncService(ctx *appContext.AppContext) *PermissionSyncService {
	return &PermissionSyncService{repo: NewRepository(ctx.Db)}
}

// Register 注册模块声明的权限
func (s *PermissionSyncService) Register(definitions ...PermissionDTO) {
	s.definitions = append(s.definitions, definitions...)
}

// Sync 将各模块声明的权限同步到 sys_permission（upsert）
func (s *PermissionSyncService) Sync(ctx context.Context) error {
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

	syncCtx := context.WithValue(ctx, auth.CurUserKey{}, syncSystemUser{})
	return s.repo.Transaction(syncCtx, func(txCtx context.Context) error {
		existing, err := s.repo.FindAllTypesOrdered(txCtx)
		if err != nil {
			return err
		}
		byCode := make(map[string]*Permission, len(existing))
		for i := range existing {
			byCode[existing[i].Code] = &existing[i]
		}

		db := s.repo.GetDB(txCtx)
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
				if err := db.Create(&entity).Error; err != nil {
					return errs.Wrap(err)
				}
				inserted++
				continue
			}
			if err := db.Model(permission).Updates(map[string]any{
				"module":      definition.Module,
				"module_name": definition.ModuleName,
				"name":        definition.Name,
				"sort_order":  definition.SortOrder,
			}).Error; err != nil {
				return errs.Wrap(err)
			}
			updated++
		}
		log.Infof("权限类型同步完成，新增 %d 个，更新 %d 个", inserted, updated)
		return nil
	})
}

// Initializer 安全模块初始化
type Initializer struct {
	repo *Repository
}

// NewSecurityInitializer 创建安全初始化服务
func NewSecurityInitializer(ctx *appContext.AppContext) *Initializer {
	return &Initializer{repo: NewRepository(ctx.Db)}
}

// Initialize 确保管理员、普通用户角色存在，并补齐管理员权限配置的查看和修改权限
func (s *Initializer) Initialize(ctx context.Context) error {
	syncCtx := context.WithValue(ctx, auth.CurUserKey{}, syncSystemUser{})
	return s.repo.Transaction(syncCtx, func(txCtx context.Context) error {
		admin, err := s.ensureRole(txCtx, AdminRoleCode, "管理员", 1)
		if err != nil {
			return err
		}
		if _, err := s.ensureRole(txCtx, "USER", "普通用户", 2); err != nil {
			return err
		}

		existing, err := s.repo.FindPermissionsByRoleID(txCtx, admin.ID)
		if err != nil {
			return err
		}
		added := 0
		for _, code := range []string{QueryAuthCode, UpdateAuthCode} {
			if slices.Contains(existing, code) {
				continue
			}
			permission, err := s.repo.FindPermissionByCode(txCtx, code)
			if err != nil {
				return err
			}
			if permission == nil {
				return errs.NewBizError("管理员初始化失败：缺少权限配置所需权限")
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
			log.Infof("管理员角色权限不完整，已授予 %d 项权限配置权限", added)
		}
		return nil
	})
}

func (s *Initializer) ensureRole(ctx context.Context, code, name string, sortOrder int) (*Role, error) {
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

// syncSystemUser 启动同步使用的系统身份
type syncSystemUser struct{}

func (syncSystemUser) GetID() uint { return 0 }
