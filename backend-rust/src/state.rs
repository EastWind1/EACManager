use std::ops::Deref;
use std::sync::Arc;

use crate::cache::Cache;
use crate::config::Config;
use crate::db::Db;

/// 应用级基础设施。`AppState` 是廉价句柄：克隆只是一次原子自增，
/// handler 用 `State<AppState>` 取用时不会复制配置或连接池。
#[derive(Clone)]
pub struct AppState {
    inner: Arc<Inner>,
}

pub struct Inner {
    pub config: Config,
    pub db: Db,
    pub cache: Cache,
}

impl AppState {
    pub async fn new(config: Config) -> Result<Self, String> {
        let db = Db::connect(&config.db).await?;
        let cache = Cache::new(&config.cache);
        Ok(Self {
            inner: Arc::new(Inner { config, db, cache }),
        })
    }
}

impl Deref for AppState {
    type Target = Inner;

    fn deref(&self) -> &Inner {
        &self.inner
    }
}
