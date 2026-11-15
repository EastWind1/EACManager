use axum::Json;
use axum::extract::{Query, State};
use axum::http::header;
use axum::response::{IntoResponse, Response};
use serde_json::Value;

use crate::auth::AuthUser;
use crate::error::{AppError, AppResult};
use crate::model::{InitStatus, ResultBody, ok};
use crate::security::model::{LoginParam, UserRequest};
use crate::security::{authenticate, require_permission};
use crate::state::AppState;
use crate::user::service;

const COOKIE_NAME: &str = "X-Auth-Token";

pub async fn login(
    State(state): State<AppState>,
    Json(param): Json<LoginParam>,
) -> AppResult<Response> {
    if param.username.trim().is_empty() {
        return Err(AppError::Biz("用户名不能为空".into()));
    }
    if param.password.is_empty() {
        return Err(AppError::Biz("密码不能为空".into()));
    }
    let jwt = &state.config.jwt;
    let (token, user) = service::login(
        &state.db,
        &jwt.secret,
        jwt.expire,
        &param.username,
        &param.password,
    )
    .await?;
    let cookie = format!(
        "{COOKIE_NAME}={token}; Path=/api; Max-Age={}; HttpOnly; Secure; SameSite=Strict",
        jwt.expire
    );
    Ok(([(header::SET_COOKIE, cookie)], ok(user)).into_response())
}

pub async fn initialization_status(State(state): State<AppState>) -> AppResult<Json<ResultBody>> {
    let required = authenticate::is_initialization_required(&state.db).await?;
    Ok(ok(InitStatus { required }))
}

pub async fn initialize(
    State(state): State<AppState>,
    Json(request): Json<UserRequest>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::initialize(&state.db, &request).await?))
}

/// 只销毁 cookie：JWT 本身无状态，登出后旧 token 仍有效，与 Java/Go 一致。
pub async fn logout() -> Response {
    let cookie = format!(
        "{COOKIE_NAME}=; Path=/api; Max-Age=-1; Expires=Thu, 01 Jan 1970 00:00:00 GMT; \
         HttpOnly; Secure; SameSite=Strict"
    );
    ([(header::SET_COOKIE, cookie)], ok(Value::Null)).into_response()
}

pub async fn get_all(
    State(state): State<AppState>,
    user: AuthUser,
    Query(param): Query<crate::model::QueryParam>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, "user:query").await?;
    Ok(ok(service::get_all(&state.db, user.id, &param).await?))
}

pub async fn create(
    State(state): State<AppState>,
    user: AuthUser,
    Json(request): Json<UserRequest>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, "user:create").await?;
    Ok(ok(service::create(&state.db, &request).await?))
}

pub async fn update(
    State(state): State<AppState>,
    user: AuthUser,
    Json(request): Json<UserRequest>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, "user:update").await?;
    Ok(ok(service::update(&state.db, &request).await?))
}
