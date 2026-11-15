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
use model::{AssignParam, BillQueryParam, BillRequest, FinishParam, ProcessedParam};

const PERM_QUERY: &str = "serviceBill:query";
const PERM_DETAIL: &str = "serviceBill:detail";
const PERM_CREATE: &str = "serviceBill:create";
const PERM_UPDATE: &str = "serviceBill:update";
const PERM_DELETE: &str = "serviceBill:delete";
const PERM_IMPORT: &str = "serviceBill:import";
const PERM_EXPORT: &str = "serviceBill:export";
const PERM_PROCESS: &str = "serviceBill:process";
const PERM_PROCESSED: &str = "serviceBill:processed";
const PERM_FINISH: &str = "serviceBill:finish";
const PERM_CANCEL_PROCESS: &str = "serviceBill:cancel-process";
const PERM_CANCEL_PROCESSED: &str = "serviceBill:cancel-processed";
const PERM_CANCEL_FINISH: &str = "serviceBill:cancel-finish";
const PERM_STATISTIC: &str = "serviceBill:statistic";
const PERM_ASSIGN: &str = "serviceBill:assign";

async fn guard_query(
    State(app): State<AppState>,
    user: AuthUser,
    Json(param): Json<BillQueryParam>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_QUERY).await?;
    query_bills(State(app), user, Json(param)).await
}

async fn guard_detail(
    State(app): State<AppState>,
    user: AuthUser,
    Path(id): Path<i32>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_DETAIL).await?;
    get_bill(State(app), user, Path(id)).await
}

async fn guard_create(
    State(app): State<AppState>,
    user: AuthUser,
    Json(request): Json<BillRequest>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_CREATE).await?;
    create_bill(State(app), user, Json(request)).await
}

async fn guard_update(
    State(app): State<AppState>,
    user: AuthUser,
    Json(request): Json<BillRequest>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_UPDATE).await?;
    update_bill(State(app), user, Json(request)).await
}

async fn guard_delete(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_DELETE).await?;
    delete_bills(State(app), user, Json(ids)).await
}

async fn guard_process(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_PROCESS).await?;
    process_bills(State(app), user, Json(ids)).await
}

async fn guard_processed(
    State(app): State<AppState>,
    user: AuthUser,
    Json(param): Json<ProcessedParam>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_PROCESSED).await?;
    processed_bills(State(app), user, Json(param)).await
}

async fn guard_finish(
    State(app): State<AppState>,
    user: AuthUser,
    Json(param): Json<FinishParam>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_FINISH).await?;
    finish_bills(State(app), user, Json(param)).await
}

async fn guard_cancel_process(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_CANCEL_PROCESS).await?;
    cancel_process_bills(State(app), user, Json(ids)).await
}

async fn guard_cancel_processed(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_CANCEL_PROCESSED).await?;
    cancel_processed_bills(State(app), user, Json(ids)).await
}

async fn guard_cancel_finish(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_CANCEL_FINISH).await?;
    cancel_finish_bills(State(app), user, Json(ids)).await
}

async fn guard_assign(
    State(app): State<AppState>,
    user: AuthUser,
    Json(param): Json<AssignParam>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_ASSIGN).await?;
    assign_bills(State(app), user, Json(param)).await
}

async fn guard_assignees(
    State(app): State<AppState>,
    user: AuthUser,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_ASSIGN).await?;
    list_assignees(State(app), user).await
}

async fn guard_import(
    State(app): State<AppState>,
    user: AuthUser,
    Json(param): Json<handler::ImportFileParam>,
) -> AppResult<Json<ResultBody>> {
    require_permission(&app, &user, PERM_IMPORT).await?;
    import_bill(State(app), user, Json(param)).await
}

async fn guard_export(
    State(app): State<AppState>,
    user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<axum::response::Response> {
    require_permission(&app, &user, PERM_EXPORT).await?;
    export_bills(State(app), user, Json(ids)).await
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
        .route(
            "/api/serviceBill/query",
            post(guard_query).get(guard_query), // 支持 GET 形式查询
        )
        .route("/api/serviceBill/{id}", get(guard_detail))
        .route("/api/serviceBill/", post(guard_create))
        .route("/api/serviceBill/", put(guard_update))
        .route("/api/serviceBill/", delete(guard_delete))
        .route("/api/serviceBill/process", put(guard_process))
        .route("/api/serviceBill/processed", put(guard_processed))
        .route("/api/serviceBill/finish", put(guard_finish))
        .route("/api/serviceBill/cancel-process", put(guard_cancel_process))
        .route(
            "/api/serviceBill/cancel-processed",
            put(guard_cancel_processed),
        )
        .route("/api/serviceBill/cancel-finish", put(guard_cancel_finish))
        .route("/api/serviceBill/assign", put(guard_assign))
        .route("/api/serviceBill/assignees", get(guard_assignees))
        .route("/api/serviceBill/import", post(guard_import))
        .route("/api/serviceBill/export", post(guard_export))
        .route("/api/serviceBill/countByState", get(guard_count_by_state))
        .route(
            "/api/serviceBill/totalAmountGroupByMonth",
            get(guard_total_amount_group_by_month),
        )
}
