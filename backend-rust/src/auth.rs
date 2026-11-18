use std::sync::LazyLock;

use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use chrono::{Duration as ChronoDuration, Utc};
use jsonwebtoken::{Algorithm, DecodingKey, EncodingKey, Header, Validation, decode, encode};
use regex::Regex;
use serde::{Deserialize, Serialize};

use crate::error::AppError;

tokio::task_local! {
    /// 当前请求的登录主体，由认证中间件用 `scope` 绑定到请求 future 上。
    ///
    /// 主体是 `Copy` 的小结构，取用不产生分配；只承载请求级上下文，
    /// 事务等资源一律显式传参——它们有跨请求的生命周期，藏在上下文里会掩盖事务边界。
    static CURRENT_USER: Option<AuthUser>;
}

/// 认证主体：只带账户 id。
///
/// 用户名在认证阶段已用于查账户，向下游没有消费方；角色权限按请求现查，
/// 因此改角色权限后下一次请求立即生效。
#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub struct AuthUser {
    pub id: i32,
}

/// axum 提取器：中间件已把主体绑定到请求 future，handler 直接声明 `user: AuthUser`
impl<S: Send + Sync> FromRequestParts<S> for AuthUser {
    type Rejection = AppError;

    async fn from_request_parts(_parts: &mut Parts, _state: &S) -> Result<Self, Self::Rejection> {
        current_user().ok_or_else(|| AppError::Unauthorized("未登录".into()))
    }
}

pub fn current_user() -> Option<AuthUser> {
    CURRENT_USER.try_with(|user| *user).ok().flatten()
}

pub fn current_user_id() -> Option<i32> {
    CURRENT_USER
        .try_with(|user| user.as_ref().map(|user| user.id))
        .ok()
        .flatten()
}

pub fn require_user() -> Result<AuthUser, AppError> {
    current_user().ok_or_else(|| AppError::Unauthorized("未登录".into()))
}

/// 在给定主体下执行请求 future，由认证中间件调用
pub async fn scope<T>(user: Option<AuthUser>, future: impl std::future::Future<Output = T>) -> T {
    CURRENT_USER.scope(user, future).await
}

static USERNAME_PATTERN: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^[A-Za-z][A-Za-z0-9_]{3,31}$").expect("username regex"));
static PASSWORD_PATTERN: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^[a-zA-Z0-9*\-_^~]+$").expect("password regex"));
static ROLE_CODE_PATTERN: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^[A-Z][A-Z0-9_]*$").expect("role code regex"));
pub static PHONE_PATTERN: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^1[3-9]\d{9}$").expect("phone regex"));

pub fn valid_username(username: &str) -> Result<(), AppError> {
    if !USERNAME_PATTERN.is_match(username.trim()) {
        return Err(AppError::Biz(
            "用户名必须为 4-32 位，以字母开头，仅包含字母、数字和下划线".into(),
        ));
    }
    Ok(())
}

pub fn valid_password(password: &str) -> Result<(), AppError> {
    if password.len() < 10 {
        return Err(AppError::Biz("密码不能小于 10 位".into()));
    }
    if password.len() > 72 {
        return Err(AppError::Biz("密码不能大于 72 位".into()));
    }
    if !PASSWORD_PATTERN.is_match(password) {
        return Err(AppError::Biz(
            "密码只能包含大写、小写字母、数字以及 *-_^~".into(),
        ));
    }
    let mut classes = 0;
    classes += usize::from(password.chars().any(|c| c.is_ascii_lowercase()));
    classes += usize::from(password.chars().any(|c| c.is_ascii_uppercase()));
    classes += usize::from(password.chars().any(|c| c.is_ascii_digit()));
    classes += usize::from(password.chars().any(|c| !c.is_ascii_alphanumeric()));
    if classes < 2 {
        return Err(AppError::Biz(
            "密码必须包含大写、小写字母、数字以及 *-_^~ 这四种符号的至少两种".into(),
        ));
    }
    Ok(())
}

pub fn valid_role_code(code: &str) -> Result<(), AppError> {
    if !ROLE_CODE_PATTERN.is_match(code) {
        return Err(AppError::Biz(
            "角色编码必须为大写字母、数字或下划线，且不能以数字开头".into(),
        ));
    }
    if code.starts_with("ROLE_") {
        return Err(AppError::Biz("角色编码不需要 ROLE_ 前缀".into()));
    }
    Ok(())
}

#[derive(Debug, Serialize, Deserialize)]
struct Claims {
    sub: String,
    exp: i64,
    iat: i64,
}

pub fn hash_password(password: &str) -> Result<String, AppError> {
    bcrypt::hash(password, bcrypt::DEFAULT_COST)
        .map_err(|error| AppError::Internal(format!("密码加密失败: {error}")))
}

pub fn verify_password(password: &str, hash: &str) -> bool {
    bcrypt::verify(password, hash).unwrap_or(false)
}

pub fn generate_token(
    secret: &str,
    expire_seconds: i64,
    subject: &str,
) -> Result<String, AppError> {
    let now = Utc::now();
    let claims = Claims {
        sub: subject.to_string(),
        iat: now.timestamp(),
        exp: (now + ChronoDuration::seconds(expire_seconds)).timestamp(),
    };
    let encoding_key = EncodingKey::from_secret(secret.as_bytes())
        .map_err(|error| AppError::Internal(format!("生成 Token 失败: {error}")))?;
    encode(&Header::new(Algorithm::HS256), &claims, &encoding_key)
        .map_err(|error| AppError::Internal(format!("生成 Token 失败: {error}")))
}

pub fn verify_token(secret: &str, token: &str) -> Result<String, AppError> {
    let mut validation = Validation::new(Algorithm::HS256);
    validation.validate_exp = true;
    let decoding_key = DecodingKey::from_secret(secret.as_bytes())
        .map_err(|_| AppError::Unauthorized("Token 无效".into()))?;
    let data = decode::<Claims>(token, &decoding_key, &validation)
        .map_err(|_| AppError::Unauthorized("Token 无效".into()))?;
    Ok(data.claims.sub)
}
