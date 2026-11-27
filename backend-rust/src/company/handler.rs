use axum::Json;
use axum::extract::{Path, Query, State};

use crate::auth::AuthUser;
use crate::company::model::CompanyRequest;
use crate::company::service;
use crate::error::AppResult;
use crate::model::{QueryParam, ResultBody, ok, ok_empty};
use crate::security::require_permission;
use crate::state::AppState;

pub async fn get_all(
    State(state): State<AppState>,
    user: AuthUser,
    Query(param): Query<QueryParam>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, "company:query").await?;
    Ok(ok(service::find_enabled(&state.db, &param).await?))
}

pub async fn create(
    State(state): State<AppState>,
    user: AuthUser,
    Json(request): Json<CompanyRequest>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, "company:create").await?;
    Ok(ok(service::create(&state, &request).await?))
}

pub async fn update(
    State(state): State<AppState>,
    user: AuthUser,
    Json(request): Json<CompanyRequest>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, "company:update").await?;
    Ok(ok(service::update(&state, &request).await?))
}

pub async fn disable(
    State(state): State<AppState>,
    user: AuthUser,
    Path(id): Path<i32>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, "company:disable").await?;
    service::disable(&state, id).await?;
    Ok(ok_empty())
}
