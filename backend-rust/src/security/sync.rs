use std::collections::HashSet;

use crate::error::{AppError, AppResult};
use crate::state::AppState;

use super::model::PermissionDto;
use super::store::{self, PermissionRow};
use super::{ADMIN_ROLE_CODE, AUTH_USER_CACHE, module_declarations};

/// 把各模块声明的权限同步到 sys_permission（只增改，不删库中额外项）
pub async fn sync_permissions(state: &AppState) -> AppResult<()> {
    let definitions = module_declarations();
    if definitions.is_empty() {
        tracing::warn!("未发现任何模块权限定义");
        return Ok(());
    }
    // 编码重复说明两个模块声明了同一个动作，属于编码错误，启动即失败
    {
        let mut codes = HashSet::new();
        for definition in &definitions {
            if !codes.insert(definition.code.as_str()) {
                return Err(AppError::Biz(format!(
                    "重复的权限编码: {}",
                    definition.code
                )));
            }
        }
    }

    // 权限定义只用于同步一次，字段直接移动
    let rows: Vec<PermissionRow> = definitions.into_iter().map(PermissionRow::from).collect();
    let (inserted, updated) = state
        .db
        .transaction(async |conn| store::upsert_permissions(conn, &rows).await)
        .await?;
    tracing::info!("权限类型同步完成，新增 {inserted} 个，更新 {updated} 个");
    Ok(())
}

/// 确保管理员、普通用户角色存在，并为管理员补齐全部权限
pub async fn init_admin(state: &AppState) -> AppResult<()> {
    state
        .db
        .transaction(async |conn| {
            let admin = match store::lock_role_by_code(conn, ADMIN_ROLE_CODE).await? {
                Some(role) => role,
                None => {
                    let role = store::create_role(conn, ADMIN_ROLE_CODE, "管理员", 1).await?;
                    tracing::info!("角色不存在，已创建: {ADMIN_ROLE_CODE}");
                    role
                }
            };
            if store::find_role_by_code(&state.db, "USER").await?.is_none() {
                store::create_role(conn, "USER", "普通用户", 2).await?;
                tracing::info!("角色不存在，已创建: USER");
            }
            let granted: HashSet<String> =
                store::find_permission_codes_by_role_id(&state.db, admin.id)
                    .await?
                    .into_iter()
                    .collect();
            let permissions = store::find_all_permissions_ordered(&state.db).await?;
            let missing: Vec<i32> = permissions
                .iter()
                .filter(|permission| !granted.contains(&permission.code))
                .map(|permission| permission.id)
                .collect();
            store::create_role_permissions(conn, admin.id, &missing).await?;
            if !missing.is_empty() {
                tracing::info!("管理员角色权限不完整，已补齐 {} 项权限", missing.len());
            }
            Ok(())
        })
        .await?;
    // 权限目录或管理员权限有调整时，旧鉴权缓存可能已过期。
    state.cache.invalidate(AUTH_USER_CACHE).await;
    Ok(())
}

impl From<PermissionDto> for PermissionRow {
    fn from(definition: PermissionDto) -> Self {
        Self {
            id: 0,
            code: definition.code,
            module: definition.module,
            module_name: definition.module_name,
            name: definition.name,
            sort_order: definition.sort_order,
        }
    }
}
