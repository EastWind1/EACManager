package security

import (
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"context"

	"gorm.io/gorm"
)

// Repository 安全模块仓库
type Repository struct {
	*database.BaseRepository[RolePermission]
}

// NewRepository 创建仓库
func NewRepository(db *gorm.DB) *Repository {
	return &Repository{BaseRepository: database.NewBaseRepository[RolePermission](db)}
}

// HasAdminUser 是否存在指定角色的用户
func (r *Repository) HasAdminUser(ctx context.Context, roleCode string) (bool, error) {
	var count int64
	err := r.GetDB(ctx).
		Table("sys_user_role").
		Joins("JOIN sys_role ON sys_role.id = sys_user_role.role_id").
		Where("sys_role.code = ?", roleCode).
		Count(&count).Error
	if err != nil {
		return false, errs.Wrap(err)
	}
	return count > 0, nil
}

// FindAllTypesOrdered 查询全部权限类型
func (r *Repository) FindAllTypesOrdered(ctx context.Context) ([]Permission, error) {
	var res []Permission
	err := r.GetDB(ctx).Order("sort_order, code").Find(&res).Error
	return res, errs.Wrap(err)
}

// FindCodesByCodes 查询存在的权限编码
func (r *Repository) FindCodesByCodes(ctx context.Context, codes []string) ([]string, error) {
	if len(codes) == 0 {
		return []string{}, nil
	}
	var res []string
	err := r.GetDB(ctx).Model(&Permission{}).Where("code IN ?", codes).Pluck("code", &res).Error
	return res, errs.Wrap(err)
}

// FindPermissionByCode 按编码查询权限
func (r *Repository) FindPermissionByCode(ctx context.Context, code string) (*Permission, error) {
	var permission Permission
	err := r.GetDB(ctx).Where("code = ?", code).Take(&permission).Error
	if err == gorm.ErrRecordNotFound {
		return nil, nil
	}
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return &permission, nil
}

// FindAllRolePermissions 查询全部角色权限
func (r *Repository) FindAllRolePermissions(ctx context.Context) ([]RolePermission, error) {
	var res []RolePermission
	err := r.GetDB(ctx).Find(&res).Error
	return res, errs.Wrap(err)
}

// FindRolePermissionsByRoleIDs 批量查询角色权限
func (r *Repository) FindRolePermissionsByRoleIDs(ctx context.Context, roleIDs []uint) ([]RolePermission, error) {
	var res []RolePermission
	if len(roleIDs) == 0 {
		return res, nil
	}
	err := r.GetDB(ctx).Where("role_id IN ?", roleIDs).Find(&res).Error
	return res, errs.Wrap(err)
}

// FindUserRolesByUserIDs 批量查询用户角色关联
func (r *Repository) FindUserRolesByUserIDs(ctx context.Context, userIDs []uint) ([]UserRole, error) {
	var res []UserRole
	if len(userIDs) == 0 {
		return res, nil
	}
	err := r.GetDB(ctx).Where("user_id IN ?", userIDs).Find(&res).Error
	return res, errs.Wrap(err)
}

// FindRolesByIDs 批量查询角色
func (r *Repository) FindRolesByIDs(ctx context.Context, ids []uint) ([]Role, error) {
	var res []Role
	if len(ids) == 0 {
		return res, nil
	}
	err := r.GetDB(ctx).Where("id IN ?", ids).Order("sort_order, id").Find(&res).Error
	return res, errs.Wrap(err)
}

// FindPermissionsByRoleID 查询角色的权限编码
func (r *Repository) FindPermissionsByRoleID(ctx context.Context, roleID uint) ([]string, error) {
	var res []string
	err := r.GetDB(ctx).
		Table("sys_role_permission").
		Select("sys_permission.code").
		Joins("JOIN sys_permission ON sys_permission.id = sys_role_permission.permission_id").
		Where("sys_role_permission.role_id = ?", roleID).
		Order("sys_permission.sort_order, sys_permission.code").
		Pluck("sys_permission.code", &res).Error
	return res, errs.Wrap(err)
}

// FindPermissionsByUserID 查询用户的权限编码
func (r *Repository) FindPermissionsByUserID(ctx context.Context, userID uint) ([]string, error) {
	var res []string
	err := r.GetDB(ctx).
		Table("sys_role_permission").
		Select("sys_permission.code").
		Joins("JOIN sys_permission ON sys_permission.id = sys_role_permission.permission_id").
		Joins("JOIN sys_user_role ON sys_user_role.role_id = sys_role_permission.role_id").
		Joins("JOIN sys_role ON sys_role.id = sys_user_role.role_id").
		Where("sys_user_role.user_id = ? AND sys_role.disabled = false", userID).
		Group("sys_permission.code, sys_permission.sort_order").
		Order("sys_permission.sort_order, sys_permission.code").
		Pluck("sys_permission.code", &res).Error
	return res, errs.Wrap(err)
}

// FindRoleCodesByUserID 查询用户的角色编码
func (r *Repository) FindRoleCodesByUserID(ctx context.Context, userID uint) ([]string, error) {
	var res []string
	err := r.GetDB(ctx).
		Table("sys_user_role").
		Select("sys_role.code").
		Joins("JOIN sys_role ON sys_role.id = sys_user_role.role_id").
		Where("sys_user_role.user_id = ? AND sys_role.disabled = false", userID).
		Order("sys_role.sort_order, sys_role.id").
		Pluck("sys_role.code", &res).Error
	return res, errs.Wrap(err)
}

type userRoleCodeRow struct {
	UserID uint
	Code   string
}

// FindRoleCodesByUserIDs 批量查询用户角色编码
func (r *Repository) FindRoleCodesByUserIDs(ctx context.Context, userIDs []uint) (map[uint][]string, error) {
	result := make(map[uint][]string)
	if len(userIDs) == 0 {
		return result, nil
	}
	var rows []userRoleCodeRow
	err := r.GetDB(ctx).
		Table("sys_user_role").
		Select("sys_user_role.user_id, sys_role.code").
		Joins("JOIN sys_role ON sys_role.id = sys_user_role.role_id").
		Where("sys_user_role.user_id IN ? AND sys_role.disabled = false", userIDs).
		Order("sys_user_role.user_id, sys_role.sort_order, sys_role.id").
		Scan(&rows).Error
	if err != nil {
		return nil, errs.Wrap(err)
	}
	for _, row := range rows {
		result[row.UserID] = append(result[row.UserID], row.Code)
	}
	return result, nil
}

// CreateRole 创建角色
func (r *Repository) CreateRole(ctx context.Context, role *Role) error {
	return errs.Wrap(r.GetDB(ctx).Create(role).Error)
}

// UpdateRole 更新角色
func (r *Repository) UpdateRole(ctx context.Context, role *Role) error {
	return errs.Wrap(r.GetDB(ctx).Save(role).Error)
}

// DeleteRole 删除角色
func (r *Repository) DeleteRole(ctx context.Context, id uint) error {
	return errs.Wrap(r.GetDB(ctx).Delete(&Role{}, id).Error)
}

// DeleteUserRolesByRoleID 删除角色的全部用户关联
func (r *Repository) DeleteUserRolesByRoleID(ctx context.Context, roleID uint) error {
	return errs.Wrap(r.GetDB(ctx).Where("role_id = ?", roleID).Delete(&UserRole{}).Error)
}

// DeleteRolePermissionsByRoleID 删除角色的全部权限关联
func (r *Repository) DeleteRolePermissionsByRoleID(ctx context.Context, roleID uint) error {
	return errs.Wrap(r.GetDB(ctx).Where("role_id = ?", roleID).Delete(&RolePermission{}).Error)
}

// DeleteUserRolesByUserID 删除用户的全部角色关联
func (r *Repository) DeleteUserRolesByUserID(ctx context.Context, userID uint) error {
	return errs.Wrap(r.GetDB(ctx).Where("user_id = ?", userID).Delete(&UserRole{}).Error)
}

// CreateUserRole 创建用户角色关联
func (r *Repository) CreateUserRole(ctx context.Context, relation *UserRole) error {
	return errs.Wrap(r.GetDB(ctx).Create(relation).Error)
}

// CreateRolePermission 创建角色权限关联
func (r *Repository) CreateRolePermission(ctx context.Context, relation *RolePermission) error {
	return errs.Wrap(r.GetDB(ctx).Create(relation).Error)
}

// CountPermissionsByIDs 统计存在的权限数量
func (r *Repository) CountPermissionsByIDs(ctx context.Context, ids []uint) (int64, error) {
	var count int64
	if len(ids) == 0 {
		return 0, nil
	}
	err := r.GetDB(ctx).Model(&Permission{}).Where("id IN ?", ids).Count(&count).Error
	return count, errs.Wrap(err)
}

// FindRolesByCodes 按编码批量查询角色
func (r *Repository) FindRolesByCodes(ctx context.Context, codes []string) ([]Role, error) {
	var res []Role
	if len(codes) == 0 {
		return res, nil
	}
	err := r.GetDB(ctx).Where("code IN ?", codes).Order("sort_order, id").Find(&res).Error
	return res, errs.Wrap(err)
}

// FindAllRoles 查询全部角色
func (r *Repository) FindAllRoles(ctx context.Context) ([]Role, error) {
	var res []Role
	err := r.GetDB(ctx).Order("sort_order, id").Find(&res).Error
	return res, errs.Wrap(err)
}

// FindRoleByID 查询角色
func (r *Repository) FindRoleByID(ctx context.Context, id uint) (*Role, error) {
	var role Role
	err := r.GetDB(ctx).Where("id = ?", id).Take(&role).Error
	if err == gorm.ErrRecordNotFound {
		return nil, nil
	}
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return &role, nil
}

// FindRoleByCode 按编码查询角色
func (r *Repository) FindRoleByCode(ctx context.Context, code string) (*Role, error) {
	var role Role
	err := r.GetDB(ctx).Where("code = ?", code).Take(&role).Error
	if err == gorm.ErrRecordNotFound {
		return nil, nil
	}
	if err != nil {
		return nil, errs.Wrap(err)
	}
	return &role, nil
}
