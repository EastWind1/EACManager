/// sqlx 0.9 只接受 `&'static str`。查询值一律走绑定参数；拼接进 SQL 的只有列名/表名常量、
/// 数字分页参数，以及调用方已做标识符白名单校验的排序片段（见 `QueryParam::order_by`）。
/// 新增调用点必须先确认插值内容不含外部输入，再使用这个断言。
macro_rules! safe_sql {
    ($($arg:tt)*) => {
        sqlx::AssertSqlSafe(format!($($arg)*))
    };
}

pub mod attach;
pub mod auth;
pub mod bill;
pub mod cache;
pub mod company;
pub mod config;
pub mod db;
pub mod error;
pub mod model;
pub mod reimburse;
pub mod security;
pub mod server;
pub mod state;
pub mod user;
pub mod util;

pub use crate::error::{AppError, AppResult};
pub use crate::state::AppState;
