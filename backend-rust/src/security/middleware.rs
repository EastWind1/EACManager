use axum::extract::Request;
use axum::http::header;
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};

use crate::auth::{self, AuthUser};
use crate::error::{AppError, AppResult};
use crate::state::AppState;

/// 认证中间件：解析 X-Auth-Token cookie 或 Authorization，公开接口直接放行。
///
/// 解析结果经 `auth::scope` 绑定到请求 future，handler 用 `AuthUser` 提取器取用。
pub async fn authenticate(state: AppState, request: Request, next: Next) -> Response {
    // 请求里需要的信息先拷成自有值：Request 不满足 Sync，
    // 持有 &Request 跨 await 会让中间件的 future 不是 Send。
    // 公开接口不必解析 token，省掉一次 cookie/header 扫描。
    let public = is_public(request.uri().path(), request.method().as_str());
    let token = if public {
        None
    } else {
        extract_token(&request)
    };

    match resolve_user(&state, public, token).await {
        Ok(user) => auth::scope(user, next.run(request)).await,
        Err(error) => error.into_response(),
    }
}

/// 解析出请求主体；公开接口返回 None（未登录）
async fn resolve_user(
    state: &AppState,
    public: bool,
    token: Option<String>,
) -> AppResult<Option<AuthUser>> {
    if public {
        return Ok(None);
    }

    let Some(token) = token else {
        if !crate::security::store::has_admin_user(&state.db, crate::security::ADMIN_ROLE_CODE)
            .await?
        {
            return Err(AppError::InitializationRequired);
        }
        return Err(AppError::Unauthorized("未登录".into()));
    };
    let username = auth::verify_token(&state.config.jwt.secret, &token)?;
    let Some(account) =
        crate::security::store::find_enabled_account_by_username(&state.db, &username).await?
    else {
        return Err(AppError::Unauthorized("未登录".into()));
    };
    Ok(Some(AuthUser { id: account.id }))
}

fn is_public(path: &str, method: &str) -> bool {
    matches!(
        (path, method),
        ("/api/user/token", "POST")
            | ("/api/user/initialization", "GET" | "POST")
            | ("/api/health", "GET")
    )
}

fn extract_token(request: &Request) -> Option<String> {
    if let Some(value) = request
        .headers()
        .get(header::AUTHORIZATION)
        .and_then(|value| value.to_str().ok())
    {
        let value = value.trim().strip_prefix("Bearer ").unwrap_or(value).trim();
        if !value.is_empty() {
            return Some(value.to_string());
        }
    }
    let cookies = request.headers().get(header::COOKIE)?.to_str().ok()?;
    cookies
        .split(';')
        .filter_map(|part| part.trim().strip_prefix("X-Auth-Token="))
        .find(|value| !value.is_empty())
        .map(str::to_string)
}

/// 当前用户是否拥有某动作权限。
///
/// 与 Java 侧一致，**故意不缓存**：改角色权限后下一次请求立即生效。
pub async fn has_permission(state: &AppState, user: &AuthUser, code: &str) -> AppResult<bool> {
    Ok(
        crate::security::store::find_permission_codes_by_user_id(&state.db, user.id)
            .await?
            .iter()
            .any(|value| value == code),
    )
}

/// 动作级权限校验，等价于 Java 的 @PreAuthorize。
pub async fn require_permission(state: &AppState, user: &AuthUser, code: &str) -> AppResult<()> {
    if has_permission(state, user, code).await? {
        return Ok(());
    }
    Err(AppError::Forbidden("没有权限".into()))
}

/// 任一权限满足即可
pub async fn require_any_permission(
    state: &AppState,
    user: &AuthUser,
    codes: &[&str],
) -> AppResult<()> {
    let granted =
        crate::security::store::find_permission_codes_by_user_id(&state.db, user.id).await?;
    if codes
        .iter()
        .any(|code| granted.iter().any(|value| value == code))
    {
        return Ok(());
    }
    Err(AppError::Forbidden("没有权限".into()))
}
