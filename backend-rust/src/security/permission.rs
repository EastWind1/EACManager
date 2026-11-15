use crate::security::model::PermissionDto;
use crate::security::{QUERY_AUTH_CODE, UPDATE_AUTH_CODE};

/// 安全模块的权限清单
pub fn module_permissions() -> Vec<PermissionDto> {
    vec![
        PermissionDto::definition(QUERY_AUTH_CODE, "security", "安全配置", "查看权限配置", 1),
        PermissionDto::definition(UPDATE_AUTH_CODE, "security", "安全配置", "修改权限配置", 2),
    ]
}
