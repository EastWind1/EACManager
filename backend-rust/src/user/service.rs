use std::collections::HashMap;

use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::model::{PageResult, QueryParam};
use crate::security::model::{
    UserDto, UserRequest, validate_email, validate_phone, validate_profile_name,
};
use crate::security::store::{self, AccountRow, ProfileRow};
use crate::security::{ADMIN_ROLE_CODE, authenticate, authorize};

/// 用户资料服务：只维护姓名、手机、邮箱。账户、登录、禁用与角色属于安全模块。
pub async fn login(
    db: &Db,
    secret: &str,
    expire: i64,
    username: &str,
    password: &str,
) -> AppResult<(String, UserDto)> {
    let (token, mut account) = authenticate::login(db, secret, expire, username, password).await?;
    let profile = store::find_profile(db, account.id).await?;
    apply_profile(&mut account, profile);
    Ok((token, account))
}

pub async fn get_all(db: &Db, current: i32, param: &QueryParam) -> AppResult<PageResult<UserDto>> {
    if !authorize::user_has_role(db, current, ADMIN_ROLE_CODE).await? {
        // 非管理员只看得到自己，避免越权枚举账户
        let Some(account) = store::find_account_by_id(db, current).await? else {
            return Ok(PageResult::empty(param));
        };
        let profile = store::find_profile(db, current).await?;
        let dto = authenticate::base_dto(account, Vec::new(), Vec::new(), profile);
        return Ok(PageResult::new(vec![dto], 1, param));
    }

    let page = authenticate::find_enabled_page(db, param).await?;
    let ids: Vec<i32> = page.items.iter().map(|item| item.id).collect();
    let mut profiles = profile_map(db, &ids).await?;
    let items = page
        .items
        .into_iter()
        .map(|mut item| {
            // 每个资料行只被一条响应使用，直接取走
            let profile = profiles.remove(&item.id);
            apply_profile(&mut item, profile);
            item
        })
        .collect();
    Ok(PageResult {
        items,
        total_count: page.total_count,
        total_pages: page.total_pages,
        page_size: page.page_size,
        page_index: page.page_index,
    })
}

pub async fn create(db: &Db, request: &UserRequest) -> AppResult<UserDto> {
    validate_request(request)?;
    let password = request.password.as_deref().unwrap_or_default();
    if password.is_empty() {
        return Err(AppError::Biz("密码不能为空".into()));
    }
    // 账户与资料必须同事务：只建成账户会留下没有姓名的用户
    let account = db
        .transaction(async |conn| {
            let account = authenticate::create_account_in(
                conn,
                db,
                &request.username,
                password,
                request.roles.as_deref(),
            )
            .await?;
            store::create_profile(
                conn,
                account.id,
                request.name_or_empty(),
                request.phone_or_empty(),
                request.email_or_empty(),
            )
            .await?;
            Ok(account)
        })
        .await?;
    Ok(with_request_profile(account, request))
}

pub async fn update(db: &Db, request: &UserRequest) -> AppResult<UserDto> {
    if request.id == 0 {
        return Err(AppError::Biz("id 不能为空".into()));
    }
    let Some(existing) = authenticate::find_by_id(db, request.id).await? else {
        return Err(AppError::Biz("用户不存在".into()));
    };
    if existing.username != request.username {
        return Err(AppError::Biz("用户名不能修改".into()));
    }
    validate_request(request)?;
    let password = request
        .password
        .as_deref()
        .filter(|value| !value.is_empty());
    // 账户、角色与密码统一由安全模块校验并提交
    let account =
        authenticate::update_account(db, request.id, request.roles.as_deref(), password).await?;

    // 资料行可能因历史数据缺失，缺失时补建
    db.transaction(async |conn| {
        if store::find_profile(db, request.id).await?.is_some() {
            store::update_profile(
                db,
                request.id,
                request.name_or_empty(),
                request.phone_or_empty(),
                request.email_or_empty(),
            )
            .await?;
        } else {
            store::create_profile(
                conn,
                request.id,
                request.name_or_empty(),
                request.phone_or_empty(),
                request.email_or_empty(),
            )
            .await?;
        }
        Ok(())
    })
    .await?;
    Ok(with_request_profile(account, request))
}

pub async fn initialize(db: &Db, request: &UserRequest) -> AppResult<UserDto> {
    validate_request(request)?;
    let password = request.password.as_deref().unwrap_or_default();
    if password.is_empty() {
        return Err(AppError::Biz("密码不能为空".into()));
    }
    let (account, _admin) = db
        .transaction(async |conn| {
            let (account, admin) =
                authenticate::initialize_in(conn, db, &request.username, password).await?;
            store::create_profile(
                conn,
                account.id,
                request.name_or_empty(),
                request.phone_or_empty(),
                request.email_or_empty(),
            )
            .await?;
            Ok((account, admin))
        })
        .await?;
    let role = crate::security::model::RoleDto::from(_admin);
    let permissions = authorize::user_authorities(db, account.id)
        .await?
        .permissions;
    let dto = authenticate::base_dto(account, vec![role], permissions, None);
    Ok(with_request_profile(dto, request))
}

pub async fn find_enabled_users(db: &Db) -> AppResult<Vec<UserDto>> {
    with_profiles(db, authenticate::find_enabled_list(db).await?).await
}

pub async fn find_by_ids(db: &Db, ids: &[i32]) -> AppResult<Vec<UserDto>> {
    with_profiles(db, authenticate::find_by_ids(db, ids).await?).await
}

pub async fn find_enabled_by_id(db: &Db, id: i32) -> AppResult<Option<UserDto>> {
    let Some(account) = store::find_account_by_id(db, id).await? else {
        return Ok(None);
    };
    if account.disabled {
        return Ok(None);
    }
    let profile = store::find_profile(db, id).await?;
    Ok(Some(authenticate::base_dto(
        account,
        Vec::new(),
        Vec::new(),
        profile,
    )))
}

async fn with_profiles(db: &Db, accounts: Vec<AccountRow>) -> AppResult<Vec<UserDto>> {
    let ids: Vec<i32> = accounts.iter().map(|account| account.id).collect();
    let mut profiles = profile_map(db, &ids).await?;
    Ok(accounts
        .into_iter()
        .map(|account| {
            // 同一请求里每个资料行只会被用一次
            let profile = profiles.remove(&account.id);
            let mut dto = authenticate::base_dto(account, Vec::new(), Vec::new(), None);
            apply_profile(&mut dto, profile);
            dto
        })
        .collect())
}

fn with_request_profile(mut dto: UserDto, request: &UserRequest) -> UserDto {
    dto.name = request.name_or_empty().to_string();
    dto.phone = request.phone_or_empty().to_string();
    dto.email = request.email_or_empty().to_string();
    dto
}

/// 资料行随响应一起消费，字段直接移动
fn apply_profile(dto: &mut UserDto, profile: Option<ProfileRow>) {
    if let Some(profile) = profile {
        dto.name = profile.name;
        dto.phone = profile.phone.unwrap_or_default();
        dto.email = profile.email.unwrap_or_default();
    }
}

async fn profile_map(db: &Db, ids: &[i32]) -> AppResult<HashMap<i32, ProfileRow>> {
    Ok(store::find_profiles(db, ids)
        .await?
        .into_iter()
        .map(|profile| (profile.user_id, profile))
        .collect())
}

fn validate_request(request: &UserRequest) -> AppResult<()> {
    validate_profile_name(request.name_or_empty())?;
    validate_phone(request.phone_or_empty())?;
    validate_email(request.email_or_empty())
}
