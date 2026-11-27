use crate::auth::AuthUser;
use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::model::{PageResult, QueryParam};
use crate::security::model::{PermissionDto, RoleDto, UserDto};
use crate::security::store::{self, AccountRow, ProfileRow};
use crate::security::{ADMIN_ROLE_CODE, AUTH_USER_CACHE, authorize, token};
use crate::state::AppState;

/// 账户服务：登录、首管理员初始化、创建/更新/禁用账户。
pub async fn is_initialization_required(db: &Db) -> AppResult<bool> {
    Ok(!authorize::has_admin_user(db).await?)
}

pub async fn login(
    db: &Db,
    secret: &str,
    expire: i64,
    username: &str,
    password: &str,
) -> AppResult<(String, UserDto)> {
    let Some(account) = store::find_account_by_username(db, username).await? else {
        return Err(AppError::Biz("用户名或密码错误".into()));
    };
    if account.disabled {
        return Err(AppError::Biz("用户已禁用".into()));
    }
    if !crate::auth::verify_password(password, &account.password) {
        tracing::warn!("登录失败: {username}");
        return Err(AppError::Biz("用户名或密码错误".into()));
    }
    let token = token::issue(secret, expire, account.id)?;
    let dto = to_dto(db, account).await?;
    tracing::info!("用户登录: {username}");
    Ok((token, dto))
}

/// 读取完整鉴权用户：先查缓存，未命中再读账户、角色与权限并按 id 缓存。
pub async fn load_user_by_id(app: &AppState, id: i32) -> AppResult<Option<AuthUser>> {
    let key = id.to_string();
    if let Some(user) = app.cache.get(AUTH_USER_CACHE, &key).await {
        return Ok(Some(user));
    }
    let Some(account) = store::find_enabled_account_by_id(&app.db, id).await? else {
        return Ok(None);
    };
    let authorities = authorize::user_authorities(&app.db, id).await?;
    let user = AuthUser {
        id: account.id,
        username: account.username,
        roles: authorities
            .roles
            .into_iter()
            .map(|role| role.code)
            .collect(),
        permissions: authorities
            .permissions
            .into_iter()
            .map(|permission| permission.code)
            .collect(),
    };
    app.cache.put(AUTH_USER_CACHE, &key, &user).await;
    Ok(Some(user))
}

/// 初始化首个管理员：角色行锁保证并发初始化串行执行。
pub async fn initialize(db: &Db, username: &str, password: &str) -> AppResult<UserDto> {
    let (account, admin) = db
        .transaction(async |conn| initialize_in(conn, db, username, password).await)
        .await?;

    let role = RoleDto::from(admin);
    let permissions = authorize::user_authorities(db, account.id)
        .await?
        .permissions;
    Ok(base_dto(account, vec![role], permissions, None))
}

/// 事务内的初始化步骤：调用方负责提交，便于与用户资料一起原子写入。
pub async fn initialize_in(
    conn: &mut sqlx::PgConnection,
    db: &Db,
    username: &str,
    password: &str,
) -> AppResult<(AccountRow, store::RoleRow)> {
    let username = username.trim();
    crate::auth::valid_username(username)?;
    crate::auth::valid_password(password)?;
    let hashed = crate::auth::hash_password(password)?;

    // 行锁让并发初始化的第二个请求在拿到锁后看到已创建的管理员
    let Some(admin) = store::lock_role_by_code(conn, ADMIN_ROLE_CODE).await? else {
        return Err(AppError::Biz("管理员角色不存在，无法初始化".into()));
    };
    if store::has_admin_user(db, ADMIN_ROLE_CODE).await? {
        return Err(AppError::Biz("系统已初始化，不能重复创建管理员".into()));
    }
    let account = store::create_account(conn, username, &hashed).await?;
    store::create_user_roles(conn, account.id, &[admin.id]).await?;
    tracing::info!("系统初始化管理员: {username}");
    Ok((account, admin))
}

pub async fn create_account(
    db: &Db,
    username: &str,
    password: &str,
    roles: Option<&[RoleDto]>,
) -> AppResult<UserDto> {
    db.transaction(async |conn| create_account_in(conn, db, username, password, roles).await)
        .await
}

/// 事务内创建账户；调用方负责提交，便于与用户资料一起原子写入。
pub async fn create_account_in(
    conn: &mut sqlx::PgConnection,
    db: &Db,
    username: &str,
    password: &str,
    roles: Option<&[RoleDto]>,
) -> AppResult<UserDto> {
    let username = username.trim();
    crate::auth::valid_username(username)?;
    crate::auth::valid_password(password)?;
    let codes = match roles.map(crate::security::model::role_codes) {
        Some(codes) if !codes.is_empty() => codes,
        _ => vec!["USER".to_string()],
    };
    assert_can_assign_roles(db, &codes).await?;
    let hashed = crate::auth::hash_password(password)?;

    let account = store::create_account(conn, username, &hashed).await?;
    let granted = assign_roles(conn, account.id, &codes).await?;
    Ok(base_dto(account, granted, Vec::new(), None))
}

pub async fn update_account(
    app: &AppState,
    id: i32,
    roles: Option<&[RoleDto]>,
    password: Option<&str>,
) -> AppResult<UserDto> {
    let db = &app.db;
    if id == 0 {
        return Err(AppError::Biz("id 不能为空".into()));
    }
    let current = crate::auth::require_user()?;
    let is_admin = authorize::user_has_role(db, current.id, ADMIN_ROLE_CODE).await?;
    if !is_admin && current.id != id {
        return Err(AppError::Forbidden("无权限修改其他用户信息".into()));
    }
    let Some(account) = store::find_account_by_id(db, id).await? else {
        return Err(AppError::Biz("用户不存在".into()));
    };
    let current_roles = authorize::role_codes_of_user(db, id).await?;
    let requested_roles = roles
        .map(crate::security::model::role_codes)
        .unwrap_or_else(|| current_roles.clone());
    if !is_admin && !same_strings(&requested_roles, &current_roles) {
        return Err(AppError::Biz("无权更改角色".into()));
    }
    // 去掉最后一个管理员会让系统失去管理入口
    let losing_admin = current_roles.iter().any(|code| code == ADMIN_ROLE_CODE)
        && !requested_roles.iter().any(|code| code == ADMIN_ROLE_CODE);
    if losing_admin && authorize::count_enabled_users_by_role(db, ADMIN_ROLE_CODE).await? <= 1 {
        return Err(AppError::Biz("必须保留至少一个启用的管理员".into()));
    }

    let new_password = match password.filter(|value| !value.is_empty()) {
        Some(password) => {
            crate::auth::valid_password(password)?;
            Some(crate::auth::hash_password(password)?)
        }
        None => None,
    };

    let granted = db
        .transaction(async |conn| {
            update_account_in(
                conn,
                id,
                roles,
                &requested_roles,
                is_admin,
                new_password.as_deref(),
            )
            .await
        })
        .await?;

    let roles = match granted {
        Some(roles) => roles,
        None => authorize::user_roles(db, id).await?,
    };
    let permissions = authorize::user_authorities(db, id).await?.permissions;
    // 角色或密码变更后丢弃该用户的鉴权缓存，下一次请求重新组装。
    app.cache.delete(AUTH_USER_CACHE, &id.to_string()).await;
    Ok(base_dto(account, roles, permissions, None))
}

/// 事务内的账户更新步骤：角色替换与密码重置都在同一事务里完成。
pub async fn update_account_in(
    conn: &mut sqlx::PgConnection,
    id: i32,
    roles: Option<&[RoleDto]>,
    requested_roles: &[String],
    is_admin: bool,
    new_password: Option<&str>,
) -> AppResult<Option<Vec<RoleDto>>> {
    let granted = match roles {
        Some(_) if is_admin => Some(assign_roles(conn, id, requested_roles).await?),
        _ => None,
    };
    if let Some(password) = new_password {
        sqlx::query("UPDATE sys_user SET password = $1, last_modified_date = now() WHERE id = $2")
            .bind(password)
            .bind(id)
            .execute(&mut *conn)
            .await?;
    }
    Ok(granted)
}

async fn assign_roles(
    conn: &mut sqlx::PgConnection,
    user_id: i32,
    codes: &[String],
) -> AppResult<Vec<RoleDto>> {
    if codes.is_empty() {
        return Ok(Vec::new());
    }
    let roles = store::find_roles_by_codes(conn, codes).await?;
    if roles.len() != codes.len() {
        return Err(AppError::Biz("存在未知角色".into()));
    }
    let ids: Vec<i32> = roles.iter().map(|role| role.id).collect();
    store::create_user_roles(conn, user_id, &ids).await?;
    Ok(roles.into_iter().map(RoleDto::from).collect())
}

pub async fn disable(app: &AppState, username: &str) -> AppResult<()> {
    let db = &app.db;
    let Some(account) = store::find_account_by_username(db, username).await? else {
        return Err(AppError::Biz("用户不存在".into()));
    };
    let current = crate::auth::require_user()?;
    if account.id == current.id {
        return Err(AppError::Biz("不能禁用自己".into()));
    }
    if authorize::user_has_role(db, account.id, ADMIN_ROLE_CODE).await?
        && authorize::count_enabled_users_by_role(db, ADMIN_ROLE_CODE).await? <= 1
    {
        return Err(AppError::Biz("必须保留至少一个启用的管理员".into()));
    }
    if !store::disable_account(db, username).await? {
        return Err(AppError::Biz("用户不存在".into()));
    }
    app.cache
        .delete(AUTH_USER_CACHE, &account.id.to_string())
        .await;
    Ok(())
}

pub async fn find_enabled_page(db: &Db, param: &QueryParam) -> AppResult<PageResult<UserDto>> {
    param.validate().map_err(AppError::Biz)?;
    let order = param.order_by().map_err(AppError::Biz)?;
    let order = match (order.as_str(), param.has_page()) {
        ("", true) => "id DESC".to_string(),
        _ => order,
    };
    let total = store::count_enabled_accounts(db).await?;
    if total == 0 {
        return Ok(PageResult::empty(param));
    }
    let accounts =
        store::find_enabled_accounts_page(db, &order, param.page_size(), param.offset()).await?;
    let ids: Vec<i32> = accounts.iter().map(|account| account.id).collect();
    let mut roles_by_user = group_roles(store::find_roles_by_user_ids(db, &ids).await?);
    let mut permissions_by_user = authorize::permissions_by_user_ids(db, &ids).await?;
    let items = accounts
        .into_iter()
        .map(|account| {
            let roles = roles_by_user.remove(&account.id).unwrap_or_default();
            let permissions = permissions_by_user.remove(&account.id).unwrap_or_default();
            base_dto(account, roles, permissions, None)
        })
        .collect();
    Ok(PageResult::new(items, total, param))
}

fn group_roles(rows: Vec<(i32, store::RoleRow)>) -> std::collections::HashMap<i32, Vec<RoleDto>> {
    let mut grouped: std::collections::HashMap<i32, Vec<RoleDto>> =
        std::collections::HashMap::new();
    for (user_id, role) in rows {
        grouped
            .entry(user_id)
            .or_default()
            .push(RoleDto::from(role));
    }
    grouped
}

pub async fn find_enabled_by_role(db: &Db, role_code: &str) -> AppResult<Vec<AccountRow>> {
    store::find_accounts_by_role(db, role_code).await
}

pub async fn find_by_ids(db: &Db, ids: &[i32]) -> AppResult<Vec<AccountRow>> {
    store::find_accounts_by_ids(db, ids).await
}

pub async fn find_enabled_list(db: &Db) -> AppResult<Vec<AccountRow>> {
    store::find_enabled_accounts(db).await
}

pub async fn find_by_id(db: &Db, id: i32) -> AppResult<Option<UserDto>> {
    let Some(account) = store::find_account_by_id(db, id).await? else {
        return Ok(None);
    };
    Ok(Some(to_dto(db, account).await?))
}

async fn to_dto(db: &Db, account: AccountRow) -> AppResult<UserDto> {
    let roles = authorize::user_roles(db, account.id).await?;
    let permissions = authorize::user_authorities(db, account.id)
        .await?
        .permissions;
    let profile = store::find_profile(db, account.id).await?;
    Ok(base_dto(account, roles, permissions, profile))
}

/// DTO 由一次响应独占：账户与资料字段直接移动，不再复制用户名和联系方式
pub fn base_dto(
    account: AccountRow,
    roles: Vec<RoleDto>,
    permissions: Vec<PermissionDto>,
    profile: Option<ProfileRow>,
) -> UserDto {
    let (name, phone, email) = match profile {
        Some(profile) => (
            profile.name,
            profile.phone.unwrap_or_default(),
            profile.email.unwrap_or_default(),
        ),
        None => (String::new(), String::new(), String::new()),
    };
    UserDto {
        id: account.id,
        username: account.username,
        password: None,
        disabled: account.disabled,
        roles,
        permissions,
        name,
        phone,
        email,
    }
}

/// 非管理员只能授予自己已持有的角色，避免越权提权。
async fn assert_can_assign_roles(db: &Db, roles: &[String]) -> AppResult<()> {
    let current = crate::auth::require_user()?;
    if authorize::user_has_role(db, current.id, ADMIN_ROLE_CODE).await? {
        return Ok(());
    }
    let owned = authorize::role_codes_of_user(db, current.id).await?;
    if roles.iter().all(|code| owned.contains(code)) {
        return Ok(());
    }
    Err(AppError::Biz("无权授予未持有的角色".into()))
}

fn same_strings(a: &[String], b: &[String]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let set: std::collections::HashSet<&String> = a.iter().collect();
    b.iter().all(|value| set.contains(value))
}
