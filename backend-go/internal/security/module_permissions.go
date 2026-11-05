package security

const (
	QueryAuthCode  = "permission:query"
	UpdateAuthCode = "permission:update"
)

// ModulePermissions 返回安全模块的权限清单，供启动装配与测试初始化复用
func ModulePermissions() []PermissionDTO {
	return []PermissionDTO{
		NewPermissionDTO(0, QueryAuthCode, "security", "安全配置", "查看权限配置", 1),
		NewPermissionDTO(0, UpdateAuthCode, "security", "安全配置", "修改权限配置", 2),
	}
}
