use axum::extract::Request;
use axum::http::HeaderMap;
use axum::http::header;
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};

use crate::auth::{self, AuthUser};
use crate::error::{AppError, AppResult};
use crate::security::{authenticate, token};
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
        return Err(AppError::Unauthorized("未登录".into()));
    };
    let user_id = token::verify(state, &token).await?;
    let Some(user) = authenticate::load_user_by_id(state, user_id).await? else {
        return Err(AppError::Unauthorized("未登录".into()));
    };
    Ok(Some(user))
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
    token_from_headers(request.headers())
}

/// 从 Authorization 或 X-Auth-Token cookie 读取 token；登出也要用同一套规则。
pub fn token_from_headers(headers: &HeaderMap) -> Option<String> {
    if let Some(value) = headers
        .get(header::AUTHORIZATION)
        .and_then(|value| value.to_str().ok())
    {
        let value = value.trim().strip_prefix("Bearer ").unwrap_or(value).trim();
        if !value.is_empty() {
            return Some(value.to_string());
        }
    }
    let cookies = headers.get(header::COOKIE)?.to_str().ok()?;
    cookies
        .split(';')
        .filter_map(|part| part.trim().strip_prefix("X-Auth-Token="))
        .find(|value| !value.is_empty())
        .map(str::to_string)
}

/// 当前用户是否拥有某动作权限；权限随鉴权用户缓存，变更时由安全模块清理。
pub async fn has_permission(_state: &AppState, user: &AuthUser, code: &str) -> AppResult<bool> {
    Ok(user.has_permission(code))
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
    _state: &AppState,
    user: &AuthUser,
    codes: &[&str],
) -> AppResult<()> {
    if codes.iter().any(|code| user.has_permission(code)) {
        return Ok(());
    }
    Err(AppError::Forbidden("没有权限".into()))
}
