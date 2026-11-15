use axum::Json;
use axum::body::Body;
use axum::extract::{Multipart, Query, State};
use axum::http::{HeaderValue, StatusCode, header};
use axum::response::Response;

use crate::attach::model::AttachParam;
use crate::attach::service::{self, UploadFile};
use crate::auth::AuthUser;
use crate::error::{AppError, AppResult};
use crate::model::{ResultBody, ok};
use crate::security::require_permission;
use crate::state::AppState;

pub async fn upload(
    State(state): State<AppState>,
    user: AuthUser,
    mut multipart: Multipart,
) -> AppResult<Json<ResultBody>> {
    require_permission(&state, &user, "attach:upload").await?;
    let mut namespace: Option<String> = None;
    let mut files = Vec::new();
    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|error| AppError::Biz(format!("解析上传内容失败: {error}")))?
    {
        match field.name() {
            Some("namespace") => {
                namespace = Some(
                    field
                        .text()
                        .await
                        .map_err(|error| AppError::Biz(format!("读取命名空间失败: {error}")))?,
                );
            }
            Some("files") => {
                let name = field.file_name().unwrap_or_default().to_string();
                let data = field
                    .bytes()
                    .await
                    .map_err(|error| AppError::Biz(format!("读取文件失败: {error}")))?;
                files.push(UploadFile {
                    name,
                    data: data.to_vec(),
                });
            }
            _ => {}
        }
    }
    let Some(namespace) = namespace.filter(|value| !value.is_empty()) else {
        return Err(AppError::Biz("附件命名空间不能为空".into()));
    };
    if files.is_empty() {
        return Err(AppError::Biz("没有文件上传".into()));
    }
    Ok(ok(service::upload(&state, &namespace, files).await?))
}

pub async fn download(
    State(state): State<AppState>,
    user: AuthUser,
    Query(param): Query<AttachParam>,
) -> AppResult<Response> {
    require_permission(&state, &user, "attach:download").await?;
    let (name, path) = service::get_resource(&state, param.id, &param.name).await?;
    let bytes = service::read_bytes(&path)?;
    let encoded = encode_filename(&name);
    let disposition = format!("attachment; filename=\"{encoded}\"; filename*=UTF-8''{encoded}");
    let mut response = Response::new(Body::from(bytes));
    *response.status_mut() = StatusCode::OK;
    let headers = response.headers_mut();
    headers.insert(
        header::CONTENT_TYPE,
        HeaderValue::from_static(files::content_type(&name)),
    );
    if let Ok(value) = HeaderValue::from_str(&disposition) {
        headers.insert(header::CONTENT_DISPOSITION, value);
    }
    Ok(response)
}

use crate::attach::files;

/// 下载响应的 Content-Disposition 用 UTF-8 百分号编码，保证中文文件名在各浏览器一致
pub fn encode_filename(name: &str) -> String {
    let mut out = String::new();
    for byte in name.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(byte as char);
            }
            _ => out.push_str(&format!("%{byte:02X}")),
        }
    }
    out
}
