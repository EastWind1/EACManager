use std::path::PathBuf;

use axum::Router;
use axum::routing::{get, post};

use crate::error::AppResult;
use crate::state::AppState;

pub mod content;
pub mod files;
pub mod handler;
pub mod model;
pub mod ocr;
pub mod office;
pub mod service;
pub mod store;

/// 只用于隔离附件存储，附件模块不解释其含义。
pub const SERVICE_BILL_NAMESPACE: &str = "SERVICE_BILL";
pub const REIMBURSE_NAMESPACE: &str = "REIMBURSEMENT";

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/api/attachment/", get(handler::download))
        .route("/api/attachment/upload", post(handler::upload))
}

/// 附件根目录：相对路径按进程工作目录展开
pub fn resolve_root(path: &str) -> AppResult<PathBuf> {
    let root = if std::path::Path::new(path).is_absolute() {
        PathBuf::from(path)
    } else {
        std::env::current_dir()?.join(path)
    };
    std::fs::create_dir_all(&root)?;
    Ok(root)
}
