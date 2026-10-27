package security

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"context"
	"errors"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// Repository 安全模块仓库
//
// 全部读写走 GORM 泛型 API；跨表关系用「子查询 + 外键 IN」表达，不拼 join 投影。
type Repository struct {
	*database.BaseRepository[RolePermission]
}

const relationBatchSize = database.RelationBatchSize

func NewRepository(db *gorm.DB) *Repository {
	return &Repository{BaseRepository: database.NewBaseRepository[RolePermission](db)}
}

// usersWithRole 是「拥有某角色编码的用户」子查询
const usersWithRole = "user_id IN (SELECT ur.user_id FROM sys_user_role ur " +
	"JOIN sys_role r ON r.id = ur.role_id WHERE r.code = ?)"

func (r *Repository) HasAdminUser(ctx context.Context, roleCode string) (bool, error) {
	count, err := gorm.G[UserRole](r.Ctx(ctx)).Where(usersWithRole, roleCode).Count(ctx, "*")
	if err != nil {
		return false, errs.Wrap(err)
	}
	return count > 0, nil
}

func (r *Repository) FindAllTypesOrdered(ctx context.Context) ([]Permission, error) {
	res, err := gorm.G[Permission](r.Ctx(ctx)).Order("sort_order, code").Find(ctx)
	return res, errs.Wrap(err)
}

func (r *Repository) CreatePermission(ctx context.Context, permission *Permission) error {
	return errs.Wrap(gorm.G[Permission](r.Ctx(ctx)).Create(ctx, permission))
}

// UpdatePermission 更新权限目录项的可变列（启动同步用）
func (r *Repository) UpdatePermission(ctx context.Context, permission *Permission) error {
	affected, err := gorm.G[*Permission](r.Ctx(ctx)).Select(
		"module", "module_name", "name", "sort_order").
		Where("id = ?", permission.ID).
		Updates(ctx, permission)
	if err != nil {
		return errs.Wrap(err)
	}
	if affected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return nil
}

func (r *Repository) FindPermissionByCode(ctx context.Context, code string) (*Permission, error) {
	permission, err := gorm.G[Permission](r.Ctx(ctx)).Where("code = ?", code).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &permission, nil
}

// FindEnabledRolesByUserID 查询用户拥有的启用角色
func (r *Repository) FindEnabledRolesByUserID(ctx context.Context, userID uint) ([]Role, error) {
	res, err := gorm.G[Role](r.Ctx(ctx)).
		Where("disabled = false").
		Where("id IN (SELECT role_id FROM sys_user_role WHERE user_id = ?)", userID).
		Order("sort_order, id").
		Find(ctx)
	return res, errs.Wrap(err)
}

func (r *Repository) FindPermissionsByIDs(ctx context.Context, ids []uint) ([]Permission, error) {
	if len(ids) == 0 {
		return []Permission{}, nil
	}
	res, err := gorm.G[Permission](r.Ctx(ctx)).
		Where("id IN ?", ids).Order("sort_order, code").Find(ctx)
	return res, errs.Wrap(err)
}

// FindPermissionsByRoleID 查询角色拥有的权限实体
func (r *Repository) FindPermissionsByRoleID(ctx context.Context, roleID uint) ([]Permission, error) {
	res, err := gorm.G[Permission](r.Ctx(ctx)).
		Where("id IN (SELECT permission_id FROM sys_role_permission WHERE role_id = ?)", roleID).
		Order("sort_order, code").
		Find(ctx)
	return res, errs.Wrap(err)
}

// FindPermissionCodesByRoleID 查询角色拥有的权限编码
func (r *Repository) FindPermissionCodesByRoleID(ctx context.Context, roleID uint) ([]string, error) {
	permissions, err := gorm.G[Permission](r.Ctx(ctx)).
		Select("code").
		Where("id IN (SELECT permission_id FROM sys_role_permission WHERE role_id = ?)", roleID).
		Order("sort_order, code").
		Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	res := make([]string, 0, len(permissions))
	for _, permission := range permissions {
		res = append(res, permission.Code)
	}
	return res, nil
}

func (r *Repository) FindPermissionsByUserID(ctx context.Context, userID uint) ([]string, error) {
	permissions, err := gorm.G[Permission](r.Ctx(ctx)).
		Select("code").
		Where(`id IN (SELECT DISTINCT rp.permission_id
			FROM sys_role_permission rp
			JOIN sys_user_role ur ON ur.role_id = rp.role_id
			JOIN sys_role r ON r.id = ur.role_id
			WHERE ur.user_id = ? AND r.disabled = false)`, userID).
		Order("sort_order, code").
		Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	res := make([]string, 0, len(permissions))
	for _, permission := range permissions {
		res = append(res, permission.Code)
	}
	return res, nil
}

func (r *Repository) FindRoleCodesByUserID(ctx context.Context, userID uint) ([]string, error) {
	roles, err := gorm.G[Role](r.Ctx(ctx)).
		Select("code").
		Where("disabled = false").
		Where("id IN (SELECT role_id FROM sys_user_role WHERE user_id = ?)", userID).
		Order("sort_order, id").
		Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	res := make([]string, 0, len(roles))
	for _, role := range roles {
		res = append(res, role.Code)
	}
	return res, nil
}

func (r *Repository) CreateRole(ctx context.Context, role *Role) error {
	err := gorm.G[Role](r.Ctx(ctx)).Create(ctx, role)
	if database.IsDuplicateKey(err) {
		return errs.NewBizError("角色编码已存在")
	}
	return errs.Wrap(err)
}

// UpdateRole 更新角色
//
// 只更新可变列；写字路径用 *Role 以触发内嵌 Audit 的更新钩子。
func (r *Repository) UpdateRole(ctx context.Context, role *Role) error {
	_, err := gorm.G[*Role](r.Ctx(ctx)).Select("name", "sort_order", "disabled").
		Where("id = ?", role.ID).Updates(ctx, role)
	return errs.Wrap(err)
}

func (r *Repository) DeleteRole(ctx context.Context, id uint) error {
	_, err := gorm.G[Role](r.Ctx(ctx)).Where("id = ?", id).Delete(ctx)
	return errs.Wrap(err)
}

func (r *Repository) DeleteUserRolesByRoleID(ctx context.Context, roleID uint) error {
	_, err := gorm.G[UserRole](r.Ctx(ctx)).Where("role_id = ?", roleID).Delete(ctx)
	return errs.Wrap(err)
}

func (r *Repository) DeleteRolePermissionsByRoleID(ctx context.Context, roleID uint) error {
	_, err := gorm.G[RolePermission](r.Ctx(ctx)).Where("role_id = ?", roleID).Delete(ctx)
	return errs.Wrap(err)
}

func (r *Repository) DeleteUserRolesByUserID(ctx context.Context, userID uint) error {
	_, err := gorm.G[UserRole](r.Ctx(ctx)).Where("user_id = ?", userID).Delete(ctx)
	return errs.Wrap(err)
}

// CreateUserRoles 批量创建用户角色关联
func (r *Repository) CreateUserRoles(ctx context.Context, relations []UserRole) error {
	if len(relations) == 0 {
		return nil
	}
	return errs.Wrap(gorm.G[UserRole](r.Ctx(ctx)).CreateInBatches(ctx, &relations, relationBatchSize))
}

func (r *Repository) CreateRolePermission(ctx context.Context, relation *RolePermission) error {
	return errs.Wrap(gorm.G[RolePermission](r.Ctx(ctx)).Create(ctx, relation))
}

func (r *Repository) CreateRolePermissions(ctx context.Context, relations []RolePermission) error {
	if len(relations) == 0 {
		return nil
	}
	return errs.Wrap(gorm.G[RolePermission](r.Ctx(ctx)).CreateInBatches(ctx, &relations, relationBatchSize))
}

func (r *Repository) FindRolesByCodes(ctx context.Context, codes []string) ([]Role, error) {
	if len(codes) == 0 {
		return []Role{}, nil
	}
	res, err := gorm.G[Role](r.Ctx(ctx)).Where("code IN ?", codes).Order("sort_order, id").Find(ctx)
	return res, errs.Wrap(err)
}

func (r *Repository) FindAllRoles(ctx context.Context) ([]Role, error) {
	res, err := gorm.G[Role](r.Ctx(ctx)).Order("sort_order, id").Find(ctx)
	return res, errs.Wrap(err)
}

func (r *Repository) FindRoleByID(ctx context.Context, id uint) (*Role, error) {
	role, err := gorm.G[Role](r.Ctx(ctx)).Where("id = ?", id).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &role, nil
}

func (r *Repository) FindRoleByCode(ctx context.Context, code string) (*Role, error) {
	role, err := gorm.G[Role](r.Ctx(ctx)).Where("code = ?", code).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &role, nil
}

// FindRoleByCodeForUpdate 按编码取角色并加行锁，未查到时返回 nil
//
// 用于首管理员初始化：并发的两次初始化会在这里串行化，后到的那次读到已提交的数据后由
// 业务层拒绝。行锁只在事务内生效，调用方必须用 Transaction 并传事务绑定的仓储。
func (r *Repository) FindRoleByCodeForUpdate(ctx context.Context, code string) (*Role, error) {
	role, err := gorm.G[Role](r.Ctx(ctx), clause.Locking{Strength: "UPDATE"}).
		Where("code = ?", code).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &role, nil
}
