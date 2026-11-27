pub mod authenticate;
pub mod authorize;
pub mod handler;
pub mod middleware;
pub mod model;
pub mod permission;
pub mod store;
pub mod sync;
pub mod token;

pub use middleware::{has_permission, require_any_permission, require_permission};
pub use model::{PermissionDto, RoleDto, UserDto};

use axum::Router;
use axum::routing::{delete, get, post, put};

use crate::state::AppState;

pub const ADMIN_ROLE_CODE: &str = "ADMIN";
pub const QUERY_AUTH_CODE: &str = "permission:query";
pub const UPDATE_AUTH_CODE: &str = "permission:update";

/// 完整鉴权用户缓存：账号、角色与权限按用户 id 整体缓存。
pub const AUTH_USER_CACHE: &str = "authUser";

/// 各模块声明的权限清单；由启动流程统一注册
pub fn module_declarations() -> Vec<PermissionDto> {
    let mut all = permission::module_permissions();
    all.extend(crate::user::model::permissions());
    all.extend(crate::company::model::permissions());
    all.extend(crate::attach::model::permissions());
    all.extend(crate::bill::model::permissions());
    all.extend(crate::reimburse::model::permissions());
    all
}

/// 启动时同步权限目录并补齐管理员角色权限
pub async fn bootstrap(state: &AppState) -> crate::error::AppResult<()> {
    sync::sync_permissions(state).await?;
    sync::init_admin(state).await?;
    Ok(())
}

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/api/security/permissions", get(handler::permissions))
        .route("/api/security/roles", get(handler::roles))
        .route("/api/security/role", post(handler::create_role))
        .route("/api/security/role", put(handler::update_role))
        .route("/api/security/role/{id}", delete(handler::delete_role))
        .route(
            "/api/security/role/{id}/permissions",
            get(handler::role_permissions),
        )
        .route(
            "/api/security/role/{id}/permissions",
            put(handler::update_role_permissions),
        )
        .route("/api/user/{username}", delete(handler::disable_account))
}
