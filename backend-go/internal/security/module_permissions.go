package security

const (
	QueryAuthCode  = "permission:query"
	UpdateAuthCode = "permission:update"
)

func modulePermissions() []PermissionDTO {
	return []PermissionDTO{
		NewPermissionDTO(0, QueryAuthCode, "security", "安全配置", "查看权限配置", 1),
		NewPermissionDTO(0, UpdateAuthCode, "security", "安全配置", "修改权限配置", 2),
	}
}
