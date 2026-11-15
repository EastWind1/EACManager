use sqlx::FromRow;
use sqlx::PgConnection;

use crate::db::{Db, current_operator_id, map_db_error};
use crate::error::{AppError, AppResult};

#[derive(Debug, Clone, FromRow)]
pub struct AccountRow {
    pub id: i32,
    pub username: String,
    pub password: String,
    pub disabled: bool,
}

#[derive(Debug, Clone, FromRow)]
pub struct RoleRow {
    pub id: i32,
    pub code: String,
    pub name: String,
    pub sort_order: i32,
    pub disabled: bool,
}

#[derive(Debug, Clone, FromRow)]
pub struct PermissionRow {
    pub id: i32,
    pub code: String,
    pub module: String,
    pub module_name: String,
    pub name: String,
    pub sort_order: i32,
}

#[derive(Debug, Clone, FromRow)]
pub struct ProfileRow {
    pub user_id: i32,
    pub name: String,
    pub phone: Option<String>,
    pub email: Option<String>,
}

const ROLE_COLUMNS: &str = "id, code, name, sort_order, disabled";
const PERMISSION_COLUMNS: &str = "id, code, module, module_name, name, sort_order";
const ACCOUNT_COLUMNS: &str = "id, username, password, disabled";

/// 拥有某角色编码的用户子查询
const USERS_WITH_ROLE: &str = "user_id IN (SELECT ur.user_id FROM sys_user_role ur \
     JOIN sys_role r ON r.id = ur.role_id WHERE r.code = $1)";

pub async fn has_admin_user(db: &Db, role_code: &str) -> AppResult<bool> {
    let count: i64 = sqlx::query_scalar(&format!(
        "SELECT count(1) FROM sys_user_role WHERE {USERS_WITH_ROLE}"
    ))
    .bind(role_code)
    .fetch_one(db.pool())
    .await?;
    Ok(count > 0)
}

pub async fn find_accounts_by_role(db: &Db, role_code: &str) -> AppResult<Vec<AccountRow>> {
    Ok(sqlx::query_as::<_, AccountRow>(&format!(
        "SELECT {ACCOUNT_COLUMNS} FROM sys_user WHERE disabled = false AND {USERS_WITH_ROLE} ORDER BY id"
    ))
    .bind(role_code)
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_all_permissions_ordered(db: &Db) -> AppResult<Vec<PermissionRow>> {
    Ok(sqlx::query_as::<_, PermissionRow>(&format!(
        "SELECT {PERMISSION_COLUMNS} FROM sys_permission ORDER BY sort_order, code"
    ))
    .fetch_all(db.pool())
    .await?)
}

/// 权限目录同步：同一事务内先读后写，因此连接由调用方传入。 */
pub async fn upsert_permissions(
    conn: &mut PgConnection,
    definitions: &[PermissionRow],
) -> AppResult<(usize, usize)> {
    let existing = sqlx::query_as::<_, PermissionRow>(&format!(
        "SELECT {PERMISSION_COLUMNS} FROM sys_permission ORDER BY sort_order, code"
    ))
    .fetch_all(&mut *conn)
    .await?;
    let mut by_code: std::collections::HashMap<String, PermissionRow> = existing
        .into_iter()
        .map(|row| (row.code.clone(), row))
        .collect();

    let operator = current_operator_id();
    let mut inserted = 0;
    let mut updated = 0;
    for definition in definitions {
        match by_code.remove(&definition.code) {
            None => {
                sqlx::query(
                    "INSERT INTO sys_permission (code, module, module_name, name, sort_order, \
                     created_date, last_modified_date, created_by_id, last_modified_by_id) \
                     VALUES ($1, $2, $3, $4, $5, now(), now(), $6, $6)",
                )
                .bind(&definition.code)
                .bind(&definition.module)
                .bind(&definition.module_name)
                .bind(&definition.name)
                .bind(definition.sort_order)
                .bind(operator)
                .execute(&mut *conn)
                .await?;
                inserted += 1;
            }
            Some(permission) => {
                sqlx::query(
                    "UPDATE sys_permission SET module = $1, module_name = $2, name = $3, \
                     sort_order = $4, last_modified_date = now(), last_modified_by_id = $5 \
                     WHERE id = $6",
                )
                .bind(&definition.module)
                .bind(&definition.module_name)
                .bind(&definition.name)
                .bind(definition.sort_order)
                .bind(operator)
                .bind(permission.id)
                .execute(&mut *conn)
                .await?;
                updated += 1;
            }
        }
    }
    Ok((inserted, updated))
}

pub async fn find_roles_by_user_ids(db: &Db, user_ids: &[i32]) -> AppResult<Vec<(i32, RoleRow)>> {
    if user_ids.is_empty() {
        return Ok(Vec::new());
    }
    #[derive(FromRow)]
    struct JoinedRow {
        user_id: i32,
        id: i32,
        code: String,
        name: String,
        sort_order: i32,
        disabled: bool,
    }
    let rows = sqlx::query_as::<_, JoinedRow>(
        "SELECT ur.user_id, r.id, r.code, r.name, r.sort_order, r.disabled \
         FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id \
         WHERE ur.user_id = ANY($1) AND r.disabled = false \
         ORDER BY ur.user_id, r.sort_order, r.id",
    )
    .bind(user_ids)
    .fetch_all(db.pool())
    .await?;
    Ok(rows
        .into_iter()
        .map(|row| {
            (
                row.user_id,
                RoleRow {
                    id: row.id,
                    code: row.code,
                    name: row.name,
                    sort_order: row.sort_order,
                    disabled: row.disabled,
                },
            )
        })
        .collect())
}

pub async fn find_enabled_roles_by_user_id(db: &Db, user_id: i32) -> AppResult<Vec<RoleRow>> {
    Ok(sqlx::query_as::<_, RoleRow>(&format!(
        "SELECT {ROLE_COLUMNS} FROM sys_role WHERE disabled = false \
         AND id IN (SELECT role_id FROM sys_user_role WHERE user_id = $1) \
         ORDER BY sort_order, id"
    ))
    .bind(user_id)
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_permissions_by_ids(db: &Db, ids: &[i32]) -> AppResult<Vec<PermissionRow>> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    Ok(sqlx::query_as::<_, PermissionRow>(&format!(
        "SELECT {PERMISSION_COLUMNS} FROM sys_permission WHERE id = ANY($1) ORDER BY sort_order, code"
    ))
    .bind(ids)
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_permissions_by_role_id(db: &Db, role_id: i32) -> AppResult<Vec<PermissionRow>> {
    Ok(sqlx::query_as::<_, PermissionRow>(&format!(
        "SELECT {PERMISSION_COLUMNS} FROM sys_permission \
         WHERE id IN (SELECT permission_id FROM sys_role_permission WHERE role_id = $1) \
         ORDER BY sort_order, code"
    ))
    .bind(role_id)
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_permission_codes_by_role_id(db: &Db, role_id: i32) -> AppResult<Vec<String>> {
    Ok(sqlx::query_scalar(
        "SELECT code FROM sys_permission \
         WHERE id IN (SELECT permission_id FROM sys_role_permission WHERE role_id = $1) \
         ORDER BY sort_order, code",
    )
    .bind(role_id)
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_permission_codes_by_user_id(db: &Db, user_id: i32) -> AppResult<Vec<String>> {
    Ok(sqlx::query_scalar(
        "SELECT code FROM sys_permission WHERE id IN (SELECT DISTINCT rp.permission_id \
         FROM sys_role_permission rp JOIN sys_user_role ur ON ur.role_id = rp.role_id \
         JOIN sys_role r ON r.id = ur.role_id WHERE ur.user_id = $1 AND r.disabled = false) \
         ORDER BY sort_order, code",
    )
    .bind(user_id)
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_role_codes_by_user_id(db: &Db, user_id: i32) -> AppResult<Vec<String>> {
    Ok(sqlx::query_scalar(
        "SELECT code FROM sys_role WHERE disabled = false \
         AND id IN (SELECT role_id FROM sys_user_role WHERE user_id = $1) ORDER BY sort_order, id",
    )
    .bind(user_id)
    .fetch_all(db.pool())
    .await?)
}

pub async fn create_role(
    conn: &mut PgConnection,
    code: &str,
    name: &str,
    sort_order: i32,
) -> AppResult<RoleRow> {
    let operator = current_operator_id();
    sqlx::query_as::<_, RoleRow>(&format!(
        "INSERT INTO sys_role (code, name, sort_order, disabled, created_date, last_modified_date, \
         created_by_id, last_modified_by_id) VALUES ($1, $2, $3, false, now(), now(), $4, $4) \
         RETURNING {ROLE_COLUMNS}"
    ))
    .bind(code)
    .bind(name)
    .bind(sort_order)
    .bind(operator)
    .fetch_one(&mut *conn)
    .await
    .map_err(|error| map_db_error(error, "角色编码已存在"))
}

pub async fn update_role(
    db: &Db,
    id: i32,
    name: &str,
    sort_order: i32,
    disabled: bool,
) -> AppResult<()> {
    let operator = current_operator_id();
    sqlx::query(
        "UPDATE sys_role SET name = $1, sort_order = $2, disabled = $3, \
         last_modified_date = now(), last_modified_by_id = $4 WHERE id = $5",
    )
    .bind(name)
    .bind(sort_order)
    .bind(disabled)
    .bind(operator)
    .bind(id)
    .execute(db.pool())
    .await?;
    Ok(())
}

pub async fn delete_role(conn: &mut PgConnection, id: i32) -> AppResult<()> {
    sqlx::query("DELETE FROM sys_user_role WHERE role_id = $1")
        .bind(id)
        .execute(&mut *conn)
        .await?;
    sqlx::query("DELETE FROM sys_role_permission WHERE role_id = $1")
        .bind(id)
        .execute(&mut *conn)
        .await?;
    sqlx::query("DELETE FROM sys_role WHERE id = $1")
        .bind(id)
        .execute(&mut *conn)
        .await?;
    Ok(())
}

pub async fn create_user_roles(
    conn: &mut PgConnection,
    user_id: i32,
    role_ids: &[i32],
) -> AppResult<()> {
    if role_ids.is_empty() {
        return Ok(());
    }
    let operator = current_operator_id();
    sqlx::query(
        "INSERT INTO sys_user_role (user_id, role_id, created_date, last_modified_date, \
         created_by_id, last_modified_by_id) \
         SELECT $1, unnest($2::int[]), now(), now(), $3, $3",
    )
    .bind(user_id)
    .bind(role_ids)
    .bind(operator)
    .execute(&mut *conn)
    .await?;
    Ok(())
}

pub async fn create_role_permissions(
    conn: &mut PgConnection,
    role_id: i32,
    permission_ids: &[i32],
) -> AppResult<()> {
    if permission_ids.is_empty() {
        return Ok(());
    }
    let operator = current_operator_id();
    sqlx::query(
        "INSERT INTO sys_role_permission (role_id, permission_id, created_date, last_modified_date, \
         created_by_id, last_modified_by_id) \
         SELECT $1, unnest($2::int[]), now(), now(), $3, $3",
    )
    .bind(role_id)
    .bind(permission_ids)
    .bind(operator)
    .execute(&mut *conn)
    .await?;
    Ok(())
}

pub async fn find_roles_by_codes(
    conn: &mut PgConnection,
    codes: &[String],
) -> AppResult<Vec<RoleRow>> {
    if codes.is_empty() {
        return Ok(Vec::new());
    }
    Ok(sqlx::query_as::<_, RoleRow>(&format!(
        "SELECT {ROLE_COLUMNS} FROM sys_role WHERE code = ANY($1) ORDER BY sort_order, id"
    ))
    .bind(codes)
    .fetch_all(&mut *conn)
    .await?)
}

pub async fn find_all_roles(db: &Db) -> AppResult<Vec<RoleRow>> {
    Ok(sqlx::query_as::<_, RoleRow>(&format!(
        "SELECT {ROLE_COLUMNS} FROM sys_role ORDER BY sort_order, id"
    ))
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_role_by_id(db: &Db, id: i32) -> AppResult<Option<RoleRow>> {
    Ok(sqlx::query_as::<_, RoleRow>(&format!(
        "SELECT {ROLE_COLUMNS} FROM sys_role WHERE id = $1"
    ))
    .bind(id)
    .fetch_optional(db.pool())
    .await?)
}

pub async fn find_role_by_code(db: &Db, code: &str) -> AppResult<Option<RoleRow>> {
    Ok(sqlx::query_as::<_, RoleRow>(&format!(
        "SELECT {ROLE_COLUMNS} FROM sys_role WHERE code = $1"
    ))
    .bind(code)
    .fetch_optional(db.pool())
    .await?)
}

/// 按编码取角色并加行锁；只在事务内调用才有意义（首管理员初始化）。
pub async fn lock_role_by_code(conn: &mut PgConnection, code: &str) -> AppResult<Option<RoleRow>> {
    Ok(sqlx::query_as::<_, RoleRow>(&format!(
        "SELECT {ROLE_COLUMNS} FROM sys_role WHERE code = $1 FOR UPDATE"
    ))
    .bind(code)
    .fetch_optional(&mut *conn)
    .await?)
}

pub async fn find_account_by_username(db: &Db, username: &str) -> AppResult<Option<AccountRow>> {
    Ok(sqlx::query_as::<_, AccountRow>(&format!(
        "SELECT {ACCOUNT_COLUMNS} FROM sys_user WHERE username = $1"
    ))
    .bind(username)
    .fetch_optional(db.pool())
    .await?)
}

pub async fn find_enabled_account_by_username(
    db: &Db,
    username: &str,
) -> AppResult<Option<AccountRow>> {
    Ok(sqlx::query_as::<_, AccountRow>(&format!(
        "SELECT {ACCOUNT_COLUMNS} FROM sys_user WHERE username = $1 AND disabled = false"
    ))
    .bind(username)
    .fetch_optional(db.pool())
    .await?)
}

pub async fn find_account_by_id(db: &Db, id: i32) -> AppResult<Option<AccountRow>> {
    Ok(sqlx::query_as::<_, AccountRow>(&format!(
        "SELECT {ACCOUNT_COLUMNS} FROM sys_user WHERE id = $1"
    ))
    .bind(id)
    .fetch_optional(db.pool())
    .await?)
}

pub async fn find_accounts_by_ids(db: &Db, ids: &[i32]) -> AppResult<Vec<AccountRow>> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    Ok(sqlx::query_as::<_, AccountRow>(&format!(
        "SELECT {ACCOUNT_COLUMNS} FROM sys_user WHERE id = ANY($1) ORDER BY id"
    ))
    .bind(ids)
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_enabled_accounts(db: &Db) -> AppResult<Vec<AccountRow>> {
    Ok(sqlx::query_as::<_, AccountRow>(&format!(
        "SELECT {ACCOUNT_COLUMNS} FROM sys_user WHERE disabled = false ORDER BY id"
    ))
    .fetch_all(db.pool())
    .await?)
}

/// 分页查询启用账户；order 已由 QueryParam::order_by 做过标识符白名单校验。
pub async fn count_enabled_accounts(db: &Db) -> AppResult<i64> {
    Ok(
        sqlx::query_scalar("SELECT count(1) FROM sys_user WHERE disabled = false")
            .fetch_one(db.pool())
            .await?,
    )
}

pub async fn find_enabled_accounts_page(
    db: &Db,
    order: &str,
    limit: i64,
    offset: i64,
) -> AppResult<Vec<AccountRow>> {
    let order_clause = if order.is_empty() {
        "ORDER BY id".to_string()
    } else {
        format!("ORDER BY {order}")
    };
    let page = if limit > 0 {
        format!("LIMIT {limit} OFFSET {offset}")
    } else {
        String::new()
    };
    Ok(sqlx::query_as::<_, AccountRow>(&format!(
        "SELECT {ACCOUNT_COLUMNS} FROM sys_user WHERE disabled = false {order_clause} {page}"
    ))
    .fetch_all(db.pool())
    .await?)
}

pub async fn create_account(
    conn: &mut PgConnection,
    username: &str,
    password: &str,
) -> AppResult<AccountRow> {
    let operator = current_operator_id();
    sqlx::query_as::<_, AccountRow>(&format!(
        "INSERT INTO sys_user (username, password, disabled, created_date, last_modified_date, \
         created_by_id, last_modified_by_id) VALUES ($1, $2, false, now(), now(), $3, $3) \
         RETURNING {ACCOUNT_COLUMNS}"
    ))
    .bind(username)
    .bind(password)
    .bind(operator)
    .fetch_one(&mut *conn)
    .await
    .map_err(|error| map_db_error(error, "用户名已存在"))
}

pub async fn update_password(db: &Db, id: i32, password: &str) -> AppResult<()> {
    let operator = current_operator_id();
    let affected = sqlx::query(
        "UPDATE sys_user SET password = $1, last_modified_date = now(), last_modified_by_id = $2 \
         WHERE id = $3",
    )
    .bind(password)
    .bind(operator)
    .bind(id)
    .execute(db.pool())
    .await?
    .rows_affected();
    if affected == 0 {
        return Err(AppError::Biz("用户不存在".into()));
    }
    Ok(())
}

pub async fn disable_account(db: &Db, username: &str) -> AppResult<bool> {
    let operator = current_operator_id();
    let affected = sqlx::query(
        "UPDATE sys_user SET disabled = true, last_modified_date = now(), last_modified_by_id = $1 \
         WHERE username = $2",
    )
    .bind(operator)
    .bind(username)
    .execute(db.pool())
    .await?
    .rows_affected();
    Ok(affected > 0)
}

pub async fn find_profiles(db: &Db, user_ids: &[i32]) -> AppResult<Vec<ProfileRow>> {
    if user_ids.is_empty() {
        return Ok(Vec::new());
    }
    Ok(sqlx::query_as::<_, ProfileRow>(
        "SELECT user_id, name, phone, email FROM user_profile WHERE user_id = ANY($1) ORDER BY user_id",
    )
    .bind(user_ids)
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_profile(db: &Db, user_id: i32) -> AppResult<Option<ProfileRow>> {
    Ok(sqlx::query_as::<_, ProfileRow>(
        "SELECT user_id, name, phone, email FROM user_profile WHERE user_id = $1",
    )
    .bind(user_id)
    .fetch_optional(db.pool())
    .await?)
}

pub async fn create_profile(
    conn: &mut PgConnection,
    user_id: i32,
    name: &str,
    phone: &str,
    email: &str,
) -> AppResult<()> {
    let operator = current_operator_id();
    sqlx::query(
        "INSERT INTO user_profile (user_id, name, phone, email, created_date, last_modified_date, \
         created_by_id, last_modified_by_id) VALUES ($1, $2, $3, $4, now(), now(), $5, $5)",
    )
    .bind(user_id)
    .bind(name)
    .bind(phone)
    .bind(email)
    .bind(operator)
    .execute(&mut *conn)
    .await?;
    Ok(())
}

pub async fn update_profile(
    db: &Db,
    user_id: i32,
    name: &str,
    phone: &str,
    email: &str,
) -> AppResult<()> {
    let operator = current_operator_id();
    let affected = sqlx::query(
        "UPDATE user_profile SET name = $1, phone = $2, email = $3, last_modified_date = now(), \
         last_modified_by_id = $4 WHERE user_id = $5",
    )
    .bind(name)
    .bind(phone)
    .bind(email)
    .bind(operator)
    .bind(user_id)
    .execute(db.pool())
    .await?
    .rows_affected();
    if affected == 0 {
        return Err(AppError::Biz("用户资料不存在".into()));
    }
    Ok(())
}
