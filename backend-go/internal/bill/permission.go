package bill

import "backend-go/internal/security"

// 服务单模块权限编码
const (
	QueryAuthCode           = "serviceBill:query"
	DetailAuthCode          = "serviceBill:detail"
	CreateAuthCode          = "serviceBill:create"
	UpdateAuthCode          = "serviceBill:update"
	DeleteAuthCode          = "serviceBill:delete"
	ImportAuthCode          = "serviceBill:import"
	ExportAuthCode          = "serviceBill:export"
	ProcessAuthCode         = "serviceBill:process"
	ProcessedAuthCode       = "serviceBill:processed"
	FinishAuthCode          = "serviceBill:finish"
	CancelProcessAuthCode   = "serviceBill:cancel-process"
	CancelProcessedAuthCode = "serviceBill:cancel-processed"
	CancelFinishAuthCode    = "serviceBill:cancel-finish"
	StatisticAuthCode       = "serviceBill:statistic"
)

// billPermissions 服务单模块声明的权限
func billPermissions() []security.PermissionDTO {
	return []security.PermissionDTO{
		security.NewPermissionDTO(0, QueryAuthCode, "serviceBill", "服务单", "查询服务单", 1),
		security.NewPermissionDTO(0, DetailAuthCode, "serviceBill", "服务单", "查看服务单", 2),
		security.NewPermissionDTO(0, CreateAuthCode, "serviceBill", "服务单", "新增服务单", 3),
		security.NewPermissionDTO(0, UpdateAuthCode, "serviceBill", "服务单", "修改服务单", 4),
		security.NewPermissionDTO(0, DeleteAuthCode, "serviceBill", "服务单", "删除服务单", 5),
		security.NewPermissionDTO(0, ImportAuthCode, "serviceBill", "服务单", "导入服务单", 6),
		security.NewPermissionDTO(0, ExportAuthCode, "serviceBill", "服务单", "导出服务单", 7),
		security.NewPermissionDTO(0, ProcessAuthCode, "serviceBill", "服务单", "开始处理服务单", 8),
		security.NewPermissionDTO(0, ProcessedAuthCode, "serviceBill", "服务单", "处理完成服务单", 9),
		security.NewPermissionDTO(0, FinishAuthCode, "serviceBill", "服务单", "回款完成服务单", 10),
		security.NewPermissionDTO(0, CancelProcessAuthCode, "serviceBill", "服务单", "取消处理服务单", 11),
		security.NewPermissionDTO(0, CancelProcessedAuthCode, "serviceBill", "服务单", "取消处理完成服务单", 12),
		security.NewPermissionDTO(0, CancelFinishAuthCode, "serviceBill", "服务单", "取消回款完成服务单", 13),
		security.NewPermissionDTO(0, StatisticAuthCode, "serviceBill", "服务单", "服务单统计", 14),
	}
}
