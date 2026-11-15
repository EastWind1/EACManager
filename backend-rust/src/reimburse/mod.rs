use axum::{
    Json, Router,
    extract::{Path, State},
    routing::{delete, get, post, put},
};

use crate::auth::AuthUser;
use crate::error::AppResult;
use crate::model::ResultBody;
use crate::security::middleware::require_permission;
use crate::state::AppState;

pub mod handler;
pub mod mapper;
pub mod model;
pub mod query;
pub mod relation;
pub mod service;
pub mod stat;
pub mod store;

use handler::*;
use model::{ReimburseQueryParam, ReimburseRequest};

const PERM_QUERY: &str = "reimburse:query";
const PERM_DETAIL: &str = "reimburse:detail";
const PERM_CREATE: &str = "reimburse:create";
const PERM_UPDATE: &str = "reimburse:update";
const PERM_DELETE: &str = "reimburse:delete";
const PERM_IMPORT: &str = "reimburse:import";
const PERM_EXPORT: &str = "reimburse:export";
const PERM_PROCESS: &str = "reimburse:process";
const PERM_FINISH: &str = "reimburse:finish";
const PERM_CANCEL_PROCESS: &str = "reimburse:cancel-process";
const PERM_CANCEL_FINISH: &str = "reimburse:cancel-finish";
const PERM_STATISTIC: &str = "reimburse:statistic";

async fn guard_query(
    State(app): State<AppState>,
    user: AuthUser,
    Json(param): Json<ReimburseQueryParam>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_QUERY).await?;
    query_reimburses(State(app), user, Json(param)).await
}

async fn guard_detail(
    State(app): State<AppState>,
    user: AuthUser,
    Path(id): Path<i32>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_DETAIL).await?;
    get_reimburse(State(app), user, Path(id)).await
}

async fn guard_create(
    State(app): State<AppState>,
    user: AuthUser,
    Json(request): Json<ReimburseRequest>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_CREATE).await?;
    create_reimburse(State(app), user, Json(request)).await
}

async fn guard_update(
    State(app): State<AppState>,
    user: AuthUser,
    Json(request): Json<ReimburseRequest>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_UPDATE).await?;
    update_reimburse(State(app), user, Json(request)).await
}

async fn guard_delete(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_DELETE).await?;
    delete_reimburses(State(app), user, Json(ids)).await
}

async fn guard_process(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_PROCESS).await?;
    process_reimburses(State(app), user, Json(ids)).await
}

async fn guard_finish(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_FINISH).await?;
    finish_reimburses(State(app), user, Json(ids)).await
}

async fn guard_cancel_process(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_CANCEL_PROCESS).await?;
    cancel_process_reimburses(State(app), user, Json(ids)).await
}

async fn guard_cancel_finish(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_CANCEL_FINISH).await?;
    cancel_finish_reimburses(State(app), user, Json(ids)).await
}

async fn guard_import(
    State(app): State<AppState>,
    user: AuthUser,
    Json(param): Json<handler::ImportFileParam>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_IMPORT).await?;
    import_reimburse(State(app), user, Json(param)).await
}

async fn guard_export(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<axum::response::Response> {
    require_permission(&app, &user, PERM_EXPORT).await?;
    export_reimburses(State(app), user, Json(ids)).await
}

async fn guard_count_by_state(
    State(app): State<AppState>,
    user: AuthUser,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_STATISTIC).await?;
    count_by_state(State(app), user).await
}

async fn guard_total_amount_group_by_month(
    State(app): State<AppState>,
    user: AuthUser,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_STATISTIC).await?;
    total_amount_group_by_month(State(app), user).await
}

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/api/reimburse/query", post(guard_query).get(guard_query))
        .route("/api/reimburse/{id}", get(guard_detail))
        .route("/api/reimburse/", post(guard_create))
        .route("/api/reimburse/", put(guard_update))
        .route("/api/reimburse/", delete(guard_delete))
        .route("/api/reimburse/process", put(guard_process))
        .route("/api/reimburse/finish", put(guard_finish))
        .route("/api/reimburse/cancel-process", put(guard_cancel_process))
        .route("/api/reimburse/cancel-finish", put(guard_cancel_finish))
        .route("/api/reimburse/import", post(guard_import))
        .route("/api/reimburse/export", post(guard_export))
        .route("/api/reimburse/countByState", get(guard_count_by_state))
        .route(
            "/api/reimburse/totalAmountGroupByMonth",
            get(guard_total_amount_group_by_month),
        )
}
