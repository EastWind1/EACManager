use chrono::Utc;

use crate::auth;
use crate::error::{AppError, AppResult};
use crate::state::AppState;

/// 登出后把 jti 写入该命名空间，直到 token 自然过期。
pub const BLACKLIST_CACHE: &str = "auth-blacklist";

/// 签发 token：subject 是用户 id，jti 用于登出黑名单。
pub fn issue(secret: &str, expire: i64, user_id: i32) -> AppResult<String> {
    auth::generate_token(secret, expire, &user_id.to_string())
}

/// 校验 token 并返回用户 id；已登出的 token 直接拒绝。
pub async fn verify(app: &AppState, token: &str) -> AppResult<i32> {
    let claims = auth::decode_token(&app.config.jwt.secret, token)?;
    if app.cache.has_flag(BLACKLIST_CACHE, &claims.jti).await {
        return Err(AppError::Unauthorized("Token 已失效".into()));
    }
    claims
        .subject
        .parse::<i32>()
        .ok()
        .filter(|id| *id > 0)
        .ok_or_else(|| AppError::Unauthorized("Token 不合法".into()))
}

/// 登出：校验签名后按剩余有效期写入黑名单，过期 token 无需处理。
pub async fn revoke(app: &AppState, token: &str) {
    let Ok(claims) = auth::decode_token(&app.config.jwt.secret, token) else {
        return;
    };
    let remaining = claims.exp - Utc::now().timestamp();
    if remaining <= 0 {
        return;
    }
    app.cache
        .set_flag(
            BLACKLIST_CACHE,
            &claims.jti,
            std::time::Duration::from_secs(remaining as u64),
        )
        .await;
}
