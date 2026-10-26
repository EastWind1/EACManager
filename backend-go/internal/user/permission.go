package user

import "backend-go/internal/security"

// 用户模块权限编码
const (
	QueryAuthCode   = "user:query"
	CreateAuthCode  = "user:create"
	UpdateAuthCode  = "user:update"
	DisableAuthCode = "user:disable"
)

// userPermissions 用户模块声明的权限
func userPermissions() []security.PermissionDTO {
	return []security.PermissionDTO{
		security.NewPermissionDTO(0, QueryAuthCode, "user", "用户管理", "查看用户", 1),
		security.NewPermissionDTO(0, CreateAuthCode, "user", "用户管理", "新增用户", 2),
		security.NewPermissionDTO(0, UpdateAuthCode, "user", "用户管理", "修改用户", 3),
		security.NewPermissionDTO(0, DisableAuthCode, "user", "用户管理", "禁用用户", 4),
	}
}
