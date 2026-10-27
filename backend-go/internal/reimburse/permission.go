package reimburse

import "backend-go/internal/security"

const (
	QueryAuthCode         = "reimburse:query"
	DetailAuthCode        = "reimburse:detail"
	CreateAuthCode        = "reimburse:create"
	UpdateAuthCode        = "reimburse:update"
	DeleteAuthCode        = "reimburse:delete"
	ImportAuthCode        = "reimburse:import"
	ExportAuthCode        = "reimburse:export"
	ProcessAuthCode       = "reimburse:process"
	FinishAuthCode        = "reimburse:finish"
	CancelProcessAuthCode = "reimburse:cancel-process"
	CancelFinishAuthCode  = "reimburse:cancel-finish"
	StatisticAuthCode     = "reimburse:statistic"
)

func reimbursePermissions() []security.PermissionDTO {
	return []security.PermissionDTO{
		security.NewPermissionDTO(0, QueryAuthCode, "reimburse", "报销单", "查询报销单", 1),
		security.NewPermissionDTO(0, DetailAuthCode, "reimburse", "报销单", "查看报销单", 2),
		security.NewPermissionDTO(0, CreateAuthCode, "reimburse", "报销单", "新增报销单", 3),
		security.NewPermissionDTO(0, UpdateAuthCode, "reimburse", "报销单", "修改报销单", 4),
		security.NewPermissionDTO(0, DeleteAuthCode, "reimburse", "报销单", "删除报销单", 5),
		security.NewPermissionDTO(0, ImportAuthCode, "reimburse", "报销单", "导入报销单", 6),
		security.NewPermissionDTO(0, ExportAuthCode, "reimburse", "报销单", "导出报销单", 7),
		security.NewPermissionDTO(0, ProcessAuthCode, "reimburse", "报销单", "提交报销单", 8),
		security.NewPermissionDTO(0, FinishAuthCode, "reimburse", "报销单", "完成报销单", 9),
		security.NewPermissionDTO(0, CancelProcessAuthCode, "reimburse", "报销单", "取消提交报销单", 10),
		security.NewPermissionDTO(0, CancelFinishAuthCode, "reimburse", "报销单", "取消完成报销单", 11),
		security.NewPermissionDTO(0, StatisticAuthCode, "reimburse", "报销单", "报销单统计", 12),
	}
}
