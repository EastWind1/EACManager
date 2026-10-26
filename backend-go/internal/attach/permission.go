package attach

import "backend-go/internal/security"

// 附件模块权限编码
const (
	DownloadAuthCode = "attach:download"
	UploadAuthCode   = "attach:upload"
)

// attachPermissions 附件模块声明的权限
func attachPermissions() []security.PermissionDTO {
	return []security.PermissionDTO{
		security.NewPermissionDTO(0, DownloadAuthCode, "attach", "附件", "下载附件", 1),
		security.NewPermissionDTO(0, UploadAuthCode, "attach", "附件", "上传附件", 2),
	}
}
