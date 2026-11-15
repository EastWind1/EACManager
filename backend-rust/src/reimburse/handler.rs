use axum::Json;
use axum::extract::{Path, State};
use axum::response::Response;
use serde::Deserialize;

use crate::attach::files;
use crate::attach::service::UploadFile;
use crate::auth::AuthUser;
use crate::error::AppResult;
use crate::model::{ResultBody, ok};
use crate::reimburse::model::{ReimburseDto, ReimburseQueryParam, ReimburseRequest};
use crate::reimburse::{query, service, stat};
use crate::state::AppState;

pub async fn query_reimburses(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(param): Json<ReimburseQueryParam>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(query::find_by_param(&app, param).await?))
}

pub async fn get_reimburse(
    State(app): State<AppState>,
    _user: AuthUser,
    Path(id): Path<i32>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(query::find_by_id(&app, id).await?))
}

pub async fn create_reimburse(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(request): Json<ReimburseRequest>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::create(&app, request).await?))
}

pub async fn update_reimburse(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(request): Json<ReimburseRequest>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::update(&app, request).await?))
}

pub async fn delete_reimburses(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::delete(&app, &ids).await?))
}

pub async fn process_reimburses(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::process(&app, &ids).await?))
}

pub async fn finish_reimburses(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::finish(&app, &ids).await?))
}

pub async fn cancel_process_reimburses(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::cancel_process(&app, &ids).await?))
}

pub async fn cancel_finish_reimburses(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(ids): Json<Vec<i32>>,
) -> AppResult<Json<ResultBody>> {
    Ok(ok(service::cancel_finish(&app, &ids).await?))
}

/// 导入请求体：前端把上传后的附件内容随请求一起提交
#[derive(Deserialize)]
pub struct ImportFileParam {
    pub file: UploadFile,
}

pub async fn import_reimburse(
    State(app): State<AppState>,
    _user: AuthUser,
    Json(param): Json<ImportFileParam>,
) -> AppResult<Json<ResultBody>> {
    let dto: ReimburseDto =
        service::generate_by_file(&app, param.file.name, param.file.data).await?;
    Ok(ok(dto))
}

pub async fn export_reimburses(
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
