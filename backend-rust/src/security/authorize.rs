use std::collections::{HashMap, HashSet};

use sqlx::PgConnection;

use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::security::model::{PermissionDto, RoleDto};
use crate::security::store::{self, PermissionRow, RoleRow};
use crate::security::{ADMIN_ROLE_CODE, QUERY_AUTH_CODE, UPDATE_AUTH_CODE};

/// 行数据只用于构造一次响应时移动字符串，避免再复制一次
impl From<RoleRow> for RoleDto {
    fn from(row: RoleRow) -> Self {
        Self {
            id: row.id,
            code: row.code,
            name: row.name,
            sort_order: row.sort_order,
            disabled: row.disabled,
        }
    }
}

impl From<PermissionRow> for PermissionDto {
    fn from(row: PermissionRow) -> Self {
        Self {
            id: row.id,
            code: row.code,
            module: row.module.clone(),
            module_name: row.module_name,
            name: row.name,
            sort_order: row.sort_order,
        }
    }
}

/// 按模块分组返回权限目录。
pub async fn permissions(db: &Db) -> AppResult<HashMap<String, Vec<PermissionDto>>> {
    let rows = store::find_all_permissions_ordered(db).await?;
    let mut groups: HashMap<String, Vec<PermissionDto>> = HashMap::new();
    for row in rows {
        // 分组键与 DTO 里的 module 同源，键必须保留一份
        groups
            .entry(row.module.clone())
            .or_default()
            .push(PermissionDto::from(row));
    }
    Ok(groups)
}

pub async fn has_admin_user(db: &Db) -> AppResult<bool> {
    store::has_admin_user(db, ADMIN_ROLE_CODE).await
}

pub async fn roles(db: &Db) -> AppResult<Vec<RoleDto>> {
    Ok(store::find_all_roles(db)
        .await?
        .into_iter()
        .map(Into::into)
        .collect())
}

pub async fn create_role(db: &Db, dto: &RoleDto) -> AppResult<RoleDto> {
    crate::auth::valid_role_code(&dto.code)?;
    validate_role_name(&dto.name)?;
    db.transaction(async |conn| {
        let row = store::create_role(conn, &dto.code, dto.name.trim(), dto.sort_order).await?;
        Ok(RoleDto::from(row))
    })
    .await
}

pub async fn update_role(db: &Db, dto: &RoleDto) -> AppResult<RoleDto> {
    if dto.id == 0 {
        return Err(AppError::Biz("角色 id 不能为空".into()));
    }
    let role = store::find_role_by_id(db, dto.id)
        .await?
        .ok_or_else(|| AppError::Biz("角色不存在".into()))?;
    if role.code == ADMIN_ROLE_CODE && dto.disabled {
        return Err(AppError::Biz("管理员角色不能禁用".into()));
    }
    validate_role_name(&dto.name)?;
    store::update_role(db, dto.id, dto.name.trim(), dto.sort_order, dto.disabled).await?;
    Ok(RoleDto {
        id: dto.id,
        code: role.code,
        name: dto.name.trim().to_string(),
        sort_order: dto.sort_order,
        disabled: dto.disabled,
    })
}

fn validate_role_name(name: &str) -> AppResult<()> {
    if name.trim().is_empty() {
        return Err(AppError::Biz("角色名称不能为空".into()));
    }
    if name.chars().count() > 64 {
        return Err(AppError::Biz("角色名称不能超过 64 个字符".into()));
    }
    Ok(())
}

pub async fn delete_role(db: &Db, id: i32) -> AppResult<()> {
    let role = store::find_role_by_id(db, id)
        .await?
        .ok_or_else(|| AppError::Biz("角色不存在".into()))?;
    if role.code == ADMIN_ROLE_CODE {
        return Err(AppError::Biz("管理员角色不能删除".into()));
    }
    db.transaction(async |conn| store::delete_role(conn, id).await)
        .await
}

pub async fn role_codes_of_user(db: &Db, user_id: i32) -> AppResult<Vec<String>> {
    store::find_role_codes_by_user_id(db, user_id).await
}

pub async fn user_roles(db: &Db, user_id: i32) -> AppResult<Vec<RoleDto>> {
    Ok(store::find_enabled_roles_by_user_id(db, user_id)
        .await?
        .into_iter()
        .map(Into::into)
        .collect())
}

pub async fn permission_codes(db: &Db, user_id: i32) -> AppResult<Vec<String>> {
    store::find_permission_codes_by_user_id(db, user_id).await
}

pub async fn user_has_role(db: &Db, user_id: i32, role_code: &str) -> AppResult<bool> {
    Ok(role_codes_of_user(db, user_id)
        .await?
        .iter()
        .any(|code| code == role_code))
}

pub async fn count_enabled_users_by_role(db: &Db, role_code: &str) -> AppResult<i64> {
    Ok(store::find_accounts_by_role(db, role_code).await?.len() as i64)
}

/// 用户角色与权限的组合；批量场景固定 3 次查询，避免 N+1。
#[derive(Debug, Clone, Default)]
pub struct AuthConfig {
    pub roles: Vec<RoleDto>,
    pub permissions: Vec<PermissionDto>,
}

pub async fn user_authorities(db: &Db, user_id: i32) -> AppResult<AuthConfig> {
    let roles = store::find_enabled_roles_by_user_id(db, user_id).await?;
    let codes = store::find_permission_codes_by_user_id(db, user_id).await?;
    // 权限目录按 code 建索引后整行取走：只克隆 code，其余字段移动，
    // 不必为每个已授权权限复制 4 个字符串。
    let mut all: Vec<Option<PermissionRow>> = store::find_all_permissions_ordered(db)
        .await?
        .into_iter()
        .map(Some)
        .collect();
    let positions: Vec<usize> = {
        let index: HashMap<&str, usize> = all
            .iter()
            .enumerate()
            .filter_map(|(position, row)| row.as_ref().map(|row| (row.code.as_str(), position)))
            .collect();
        codes
            .iter()
            .filter_map(|code| index.get(code.as_str()).copied())
            .collect()
    };
    let permissions = positions
        .into_iter()
        .filter_map(|position| all[position].take())
        .map(PermissionDto::from)
        .collect();
    Ok(AuthConfig {
        roles: roles.into_iter().map(Into::into).collect(),
        permissions,
    })
}

/// 整体替换用户的角色，返回写入后的角色列表。
pub async fn set_user_roles(db: &Db, user_id: i32, codes: &[String]) -> AppResult<Vec<RoleDto>> {
    let unique: Vec<&String> = {
        let mut seen = HashSet::new();
        codes
            .iter()
            .filter(|code| seen.insert(code.as_str()))
            .collect()
    };
    db.transaction(async |conn| {
        sqlx::query("DELETE FROM sys_user_role WHERE user_id = $1")
            .bind(user_id)
            .execute(&mut *conn)
            .await?;
        if unique.is_empty() {
            return Ok(Vec::new());
        }
        let codes: Vec<String> = unique.into_iter().cloned().collect();
        let roles = store::find_roles_by_codes(conn, &codes).await?;
        if roles.len() != codes.len() {
            return Err(AppError::Biz("存在未知角色".into()));
        }
        let ids: Vec<i32> = roles.iter().map(|role| role.id).collect();
        store::create_user_roles(conn, user_id, &ids).await?;
        Ok(roles.into_iter().map(Into::into).collect())
    })
    .await
}

pub async fn role_permissions(db: &Db, role_id: i32) -> AppResult<Vec<PermissionDto>> {
    store::find_role_by_id(db, role_id)
        .await?
        .ok_or_else(|| AppError::Biz("角色不存在".into()))?;
    Ok(store::find_permissions_by_role_id(db, role_id)
        .await?
        .into_iter()
        .map(Into::into)
        .collect())
}

pub async fn update_role_permissions(
    db: &Db,
    role_id: i32,
    permission_ids: &[i32],
) -> AppResult<Vec<PermissionDto>> {
    let role = store::find_role_by_id(db, role_id)
        .await?
        .ok_or_else(|| AppError::Biz("角色不存在".into()))?;
    let requested = unique_ids(permission_ids);

    db.transaction(async |conn| {
        let mut permissions = Vec::new();
        if !requested.is_empty() {
            let found = store::find_permissions_by_ids(db, &requested).await?;
            if found.len() != requested.len() {
                return Err(AppError::Biz("存在未知权限".into()));
            }
            permissions = found.into_iter().map(Into::into).collect();
        }
        if role.code == ADMIN_ROLE_CODE {
            // 管理员若丢掉权限配置页的查看/修改权限，权限页将自锁且无法恢复
            let codes: HashSet<&str> = permissions
                .iter()
                .map(|permission: &PermissionDto| permission.code.as_str())
                .collect();
            if !codes.contains(QUERY_AUTH_CODE) || !codes.contains(UPDATE_AUTH_CODE) {
                return Err(AppError::Biz(
                    "管理员角色必须保留权限配置的查看和修改权限".into(),
                ));
            }
        }
        sqlx::query("DELETE FROM sys_role_permission WHERE role_id = $1")
            .bind(role_id)
            .execute(&mut *conn)
            .await?;
        store::create_role_permissions(conn, role_id, &requested).await?;
        Ok(permissions)
    })
    .await
}

pub fn unique_ids(values: &[i32]) -> Vec<i32> {
    let mut seen = HashSet::new();
    values
        .iter()
        .copied()
        .filter(|value| seen.insert(*value))
        .collect()
}

#[allow(dead_code)]
fn _conn(_: &mut PgConnection) {}
