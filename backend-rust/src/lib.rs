/// sqlx 0.9 只接受 `&'static str`；SQL 均由列名/表名常量拼成，值一律走绑定参数，
/// 因此在这里显式断言拼接结果不含外部输入。
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
