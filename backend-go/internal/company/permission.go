package company

import "backend-go/internal/security"

// 公司模块权限编码
const (
	QueryAuthCode   = "company:query"
	CreateAuthCode  = "company:create"
	UpdateAuthCode  = "company:update"
	DisableAuthCode = "company:disable"
)

// companyPermissions 公司模块声明的权限
func companyPermissions() []security.PermissionDTO {
	return []security.PermissionDTO{
		security.NewPermissionDTO(0, QueryAuthCode, "company", "公司管理", "查看公司", 1),
		security.NewPermissionDTO(0, CreateAuthCode, "company", "公司管理", "新增公司", 2),
		security.NewPermissionDTO(0, UpdateAuthCode, "company", "公司管理", "修改公司", 3),
		security.NewPermissionDTO(0, DisableAuthCode, "company", "公司管理", "禁用公司", 4),
	}
}
