use std::collections::HashMap;
use std::sync::{Arc, Mutex, PoisonError};
use std::time::{Duration, Instant};

use serde::Serialize;
use serde::de::DeserializeOwned;
use serde_json::Value;

use crate::config::CacheConfig;

#[derive(Debug, Clone)]
struct Entry {
    value: Value,
    expire_at: Instant,
}

/// 进程内缓存：按命名空间分组，写入时记录过期时间，读取时惰性淘汰。
#[derive(Clone)]
pub struct Cache {
    inner: Arc<Mutex<HashMap<String, HashMap<String, Entry>>>>,
    default_expire: Duration,
}

impl Cache {
    pub fn new(config: &CacheConfig) -> Self {
        Self {
            inner: Arc::new(Mutex::new(HashMap::new())),
            default_expire: Duration::from_secs(config.expire.max(1)),
        }
    }

    pub fn get<T: DeserializeOwned>(&self, namespace: &str, key: &str) -> Option<T> {
        let mut buckets = self.inner.lock().unwrap_or_else(PoisonError::into_inner);
        let bucket = buckets.get_mut(namespace)?;
        let entry = bucket.get(key)?;
        if entry.expire_at <= Instant::now() {
            bucket.remove(key);
            return None;
        }
        serde_json::from_value(entry.value.clone()).ok()
    }

    pub fn put<T: Serialize>(&self, namespace: &str, key: &str, value: &T) {
        self.put_with_expire(namespace, key, value, self.default_expire);
    }

    pub fn put_with_expire<T: Serialize>(
        &self,
        namespace: &str,
        key: &str,
        value: &T,
        expire: Duration,
    ) {
        let Ok(value) = serde_json::to_value(value) else {
            return;
        };
        let entry = Entry {
            value,
            expire_at: Instant::now() + expire,
        };
        let mut buckets = self.inner.lock().unwrap_or_else(PoisonError::into_inner);
        buckets
            .entry(namespace.to_string())
            .or_default()
            .insert(key.to_string(), entry);
    }

    /// 命名空间整体失效；统计类缓存只在写入后按模块清理。
    pub fn invalidate(&self, namespace: &str) {
        self.inner
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .remove(namespace);
    }
}
