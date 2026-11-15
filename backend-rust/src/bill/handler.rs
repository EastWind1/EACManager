use axum::Json;
use axum::extract::{Path, State};
use axum::response::Response;
use serde::Deserialize;

use crate::attach::files;
use crate::attach::service::UploadFile;
use crate::auth::AuthUser;
use crate::bill::model::{AssignParam, BillQueryParam, BillRequest, FinishParam, ProcessedParam};
use crate::bill::{query, service, stat};
use crate::error::AppResult;
use crate::model::{ResultBody, ok};
use crate::state::AppState;

pub async fn query_bills(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(param): Json<BillQueryParam>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(query::find_by_param(&app, param).await?))
}

pub async fn get_bill(
    State(app): State<AppState>,
    _user: AuthUser,
    Path(id): Path<i32>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(query::find_by_id(&app, id).await?))
}

pub async fn create_bill(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(request): Json<BillRequest>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::create(&app, request).await?))
}

pub async fn update_bill(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(request): Json<BillRequest>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::update(&app, request).await?))
}

pub async fn delete_bills(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::delete(&app, &ids).await?))
}

pub async fn process_bills(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::process(&app, &ids).await?))
}

pub async fn processed_bills(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(param): Json<ProcessedParam>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::processed(
        &app,
        &param.ids,
        param.processed_date,
    )
    .await?))
}

pub async fn finish_bills(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(param): Json<FinishParam>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(
        service::finish(&app, &param.ids, param.finished_date).await?
    ))
}

pub async fn cancel_process_bills(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::cancel_process(&app, &ids).await?))
}

pub async fn cancel_processed_bills(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::cancel_processed(&app, &ids).await?))
}

pub async fn cancel_finish_bills(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::cancel_finish(&app, &ids).await?))
}

pub async fn assign_bills(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(param): Json<AssignParam>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::assign(&app, &param).await?))
}

pub async fn list_assignees(
    State(app): State<AppState>,
    _user: AuthUser,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(query::list_assignees(&app).await?))
}

/// 导入请求体：前端把上传后的附件内容随请求一起提交
#[derive(Deserialize)]
pub struct ImportFileParam {
    pub file: UploadFile,
}

pub async fn import_bill(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(param): Json<ImportFileParam>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::generate_by_file(
        &app,
        param.file.name,
        param.file.data,
    )
    .await?))
}

pub async fn export_bills(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Response> {
    let path = service::export(&app, &ids).await?;
    let file_name = path
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("export.zip");
    files::file_response(&path, file_name)
}

pub async fn count_by_state(
    State(app): State<AppState>,
    _user: AuthUser,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(stat::count_by_state(&app).await?))
}

pub async fn total_amount_group_by_month(
    State(app): State<AppState>,
    _user: AuthUser,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(stat::total_amount_group_by_month(&app).await?))
}
