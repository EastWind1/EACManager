pub use crate::security::model::{LoginParam, UserDto, UserRequest};

/// 用户模块的权限清单
pub fn permissions() -> Vec<crate::security::model::PermissionDto> {
    use crate::security::model::PermissionDto;
    vec![
        PermissionDto::definition("user:query", "user", "用户", "查看用户", 1),
        PermissionDto::definition("user:create", "user", "用户", "新建用户", 2),
        PermissionDto::definition("user:update", "user", "用户", "修改用户", 3),
        PermissionDto::definition("user:disable", "user", "用户", "禁用用户", 4),
    ]
}
