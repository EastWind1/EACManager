use axum::Json;
use axum::extract::{Path, State};

use crate::auth::AuthUser;
use crate::error::{AppError, AppResult};
use crate::model::{ResultBody, ok, ok_empty};
use crate::security::model::RoleDto;
use crate::security::{
    QUERY_AUTH_CODE, UPDATE_AUTH_CODE, authenticate, authorize, require_permission,
};
use crate::state::AppState;

pub async fn permissions(
    State(state): State<AppState>,
    user: AuthUser,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, QUERY_AUTH_CODE).await?;
    Ok(ok(authorize::permissions(&state.db).await?))
}

pub async fn roles(State(state): State<AppState>, user: AuthUser) -> AppResult<Json<ResultBody>> {
    crate::security::require_any_permission(&state, &user, &["user:query", QUERY_AUTH_CODE])
        .await?;
    Ok(ok(authorize::roles(&state.db).await?))
}

pub async fn create_role(
    State(state): State<AppState>,
    user: AuthUser,
    Json(dto): Json<RoleDto>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, UPDATE_AUTH_CODE).await?;
    Ok(ok(authorize::create_role(&state.db, &dto).await?))
}

pub async fn update_role(
    State(state): State<AppState>,
    user: AuthUser,
    Json(dto): Json<RoleDto>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, UPDATE_AUTH_CODE).await?;
    Ok(ok(authorize::update_role(&state.db, &dto).await?))
}

pub async fn delete_role(
    State(state): State<AppState>,
    user: AuthUser,
    Path(id): Path<i32>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, UPDATE_AUTH_CODE).await?;
    authorize::delete_role(&state.db, id).await?;
    Ok(ok_empty())
}

pub async fn role_permissions(
    State(state): State<AppState>,
    user: AuthUser,
    Path(id): Path<i32>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, QUERY_AUTH_CODE).await?;
    Ok(ok(authorize::role_permissions(&state.db, id).await?))
}

pub async fn update_role_permissions(
    State(state): State<AppState>,
    user: AuthUser,
    Path(id): Path<i32>,
    Json(permission_ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, UPDATE_AUTH_CODE).await?;
    Ok(ok(authorize::update_role_permissions(
        &state.db,
        id,
        &permission_ids,
    )
    .await?))
}

pub async fn disable_account(
    State(state): State<AppState>,
    user: AuthUser,
    Path(username): Path<String>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, "user:disable").await?;
    if username.trim().is_empty() {
        return Err(AppError::Biz("用户名为空".into()));
    }
    authenticate::disable(&state.db, &username).await?;
    Ok(ok_empty())
}
