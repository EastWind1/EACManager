package security

// 安全模块自身权限编码
const (
	QueryAuthCode  = "permission:query"
	UpdateAuthCode = "permission:update"
)

// modulePermissions 安全模块自身声明的权限
func modulePermissions() []PermissionDTO {
	return []PermissionDTO{
		NewPermissionDTO(0, QueryAuthCode, "security", "安全配置", "查看权限配置", 1),
		NewPermissionDTO(0, UpdateAuthCode, "security", "安全配置", "修改权限配置", 2),
	}
}
