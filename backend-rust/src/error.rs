use axum::Json;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use serde_json::{Value, json};
use thiserror::Error;

/// 统一错误类型。变体直接对应 HTTP 语义，由 `IntoResponse` 一处翻译成响应体。
#[derive(Debug, Clone, Error)]
pub enum AppError {
    /// 业务校验失败 → 400
    #[error("{0}")]
    Biz(String),
    /// 已登录但缺少动作权限 → 403
    #[error("{0}")]
    Forbidden(String),
    /// 未登录或凭证失效 → 401
    #[error("{0}")]
    Unauthorized(String),
    /// 文件读写失败 → 500，消息里不出现服务器路径
    #[error("{0}")]
    FileOp(String),
    /// 内部异常 → 500，细节只进日志
    #[error("{0}")]
    Internal(String),
}

pub type AppResult<T> = Result<T, AppError>;

impl AppError {
    pub fn biz(message: impl Into<String>) -> Self {
        Self::Biz(message.into())
    }

    pub fn forbidden(message: impl Into<String>) -> Self {
        Self::Forbidden(message.into())
    }

    pub fn unauthorized(message: impl Into<String>) -> Self {
        Self::Unauthorized(message.into())
    }

    pub fn internal(message: impl Into<String>) -> Self {
        Self::Internal(message.into())
    }

    pub fn file(message: impl Into<String>) -> Self {
        Self::FileOp(message.into())
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        // 错误体只构造一次，消息直接移动，不再复制一遍
        let (status, message, data) = match self {
            Self::Biz(message) => (StatusCode::BAD_REQUEST, message, Value::Null),
            Self::Forbidden(message) => (StatusCode::FORBIDDEN, message, Value::Null),
            Self::Unauthorized(message) => (StatusCode::UNAUTHORIZED, message, Value::Null),
            Self::FileOp(message) => (StatusCode::INTERNAL_SERVER_ERROR, message, Value::Null),
            Self::Internal(message) => {
                tracing::error!("内部异常: {message}");
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    "内部异常".to_string(),
                    Value::Null,
                )
            }
        };
        (status, Json(json!({ "message": message, "data": data }))).into_response()
    }
}

impl From<sqlx::Error> for AppError {
    fn from(error: sqlx::Error) -> Self {
        if crate::db::is_duplicate(&error) {
            return Self::Biz("数据已存在".into());
        }
        if crate::db::is_undefined_column(&error) {
            return Self::Biz("排序字段不合法".into());
        }
        Self::Internal(format!("数据库异常: {error}"))
    }
}

impl From<std::io::Error> for AppError {
    fn from(error: std::io::Error) -> Self {
        Self::FileOp(error.to_string())
    }
}

impl From<zip::result::ZipError> for AppError {
    fn from(error: zip::result::ZipError) -> Self {
        Self::FileOp(format!("压缩失败: {error}"))
    }
}

impl From<serde_json::Error> for AppError {
    fn from(error: serde_json::Error) -> Self {
        Self::Internal(format!("序列化失败: {error}"))
    }
}

/// 把「Result<T, E: Display>」提升成内部错误，用于第三方库返回值。
pub trait InternalResult<T> {
    fn internal(self) -> AppResult<T>;
}

impl<T, E: std::fmt::Display> InternalResult<T> for Result<T, E> {
    fn internal(self) -> AppResult<T> {
        self.map_err(|error| AppError::Internal(error.to_string()))
    }
}
