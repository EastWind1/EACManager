use std::time::Duration;

use sqlx::postgres::{PgConnection, PgPoolOptions};
use sqlx::{PgPool, Postgres};

use crate::config::DatabaseConfig;
use crate::error::{AppError, AppResult};

/// 数据库句柄：只持有连接池。
///
/// 事务不做隐式传递——需要原子性的地方显式开启，并把连接一路传给仓储函数，
/// 事务边界因此在类型上可见，也不会出现同一任务二次取连接的自锁。
#[derive(Clone)]
pub struct Db {
    pool: PgPool,
}

impl Db {
    pub async fn connect(config: &DatabaseConfig) -> Result<Self, String> {
        let pool = PgPoolOptions::new()
            .max_connections(config.max_open_conns.max(1))
            .min_connections(0)
            .idle_timeout(Duration::from_secs(600))
            .max_lifetime(Duration::from_secs(config.conn_max_lifetime.max(1) * 60))
            .acquire_timeout(Duration::from_secs(30))
            .connect(&dsn(config))
            .await
            .map_err(|error| format!("初始化数据库链接失败：{error}"))?;
        Ok(Self { pool })
    }

    pub fn pool(&self) -> &PgPool {
        &self.pool
    }

    /// 开启事务；调用方自行 commit，适合两三次语句的简单场景。
    pub async fn begin(&self) -> AppResult<sqlx::Transaction<'static, Postgres>> {
        Ok(self.pool.begin().await?)
    }

    /// 事务：闭包拿到连接，正常返回则提交，出错则回滚。
    pub async fn transaction<T, F>(&self, f: F) -> AppResult<T>
    where
        F: AsyncFnOnce(&mut PgConnection) -> AppResult<T>,
    {
        let mut transaction = self.pool.begin().await?;
        let value = f(&mut transaction).await?;
        transaction.commit().await?;
        Ok(value)
    }
}

/// 唯一键冲突（PostgreSQL 23505）
pub fn is_duplicate(error: &sqlx::Error) -> bool {
    matches!(error, sqlx::Error::Database(db) if db.code().as_deref() == Some("23505"))
}

/// 把唯一键冲突翻译成业务提示，其余错误交给 `From<sqlx::Error>`。
pub fn map_db_error(error: sqlx::Error, duplicate_message: &str) -> AppError {
    if is_duplicate(&error) {
        return AppError::Biz(duplicate_message.to_string());
    }
    AppError::from(error)
}

/// 当前操作人 ID，写入审计列（不复制用户名）
pub fn current_operator_id() -> Option<i32> {
    crate::auth::current_user_id()
}

fn dsn(config: &DatabaseConfig) -> String {
    let ssl = match config.sslmode.as_str() {
        "require" | "verify-ca" | "verify-full" => "require",
        "allow" => "prefer",
        _ => "disable",
    };
    format!(
        "postgres://{}:{}@{}:{}/{}?sslmode={ssl}",
        encode(&config.username),
        encode(&config.password),
        config.host,
        config.port,
        encode(&config.name)
    )
}

fn encode(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for byte in value.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(byte as char);
            }
            _ => out.push_str(&format!("%{byte:02X}")),
        }
    }
    out
}
