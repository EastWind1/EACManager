package attach

import "backend-go/internal/security"

const (
	DownloadAuthCode = "attach:download"
	UploadAuthCode   = "attach:upload"
)

func attachPermissions() []security.PermissionDTO {
	return []security.PermissionDTO{
		security.NewPermissionDTO(0, DownloadAuthCode, "attach", "附件", "下载附件", 1),
		security.NewPermissionDTO(0, UploadAuthCode, "attach", "附件", "上传附件", 2),
	}
}
