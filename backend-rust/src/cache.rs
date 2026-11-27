use std::time::Duration;

use redis::AsyncCommands;
use redis::aio::ConnectionManager;
use serde::Serialize;
use serde::de::DeserializeOwned;

use crate::config::{CacheConfig, RedisConfig};

/// 所有键统一加前缀，便于按命名空间批量清理，也避免和同库的其它应用冲突。
const PREFIX: &str = "eac";

/// Redis 共享缓存：值按 JSON 存取，flag 用于登出黑名单这类只关心存在性的标记。
///
/// 读写失败只记日志并当作未命中，缓存问题不应让业务请求失败。
#[derive(Clone)]
pub struct Cache {
    conn: ConnectionManager,
    default_expire: Duration,
}

impl Cache {
    pub async fn connect(config: &CacheConfig, redis: &RedisConfig) -> Result<Self, String> {
        let mut url = if redis.password.is_empty() {
            format!("redis://{}:{}/{}", redis.host, redis.port, redis.db)
        } else {
            format!(
                "redis://:{}@{}:{}/{}",
                encode(&redis.password),
                redis.host,
                redis.port,
                redis.db
            )
        };
        // 响应超时由配置控制；连接重试仍交给 ConnectionManager 的默认策略。
        if redis.timeout > 0 {
            url.push_str(&format!("?response_timeout={}", redis.timeout * 1000));
        }
        let client = redis::Client::open(url)
            .map_err(|error| format!("初始化 Redis 客户端失败: {error}"))?;
        let conn = ConnectionManager::new(client)
            .await
            .map_err(|error| format!("连接 Redis 缓存失败: {error}"))?;
        tracing::info!(
            "Redis 缓存已连接: {}:{} db={}",
            redis.host,
            redis.port,
            redis.db
        );
        Ok(Self {
            conn,
            default_expire: Duration::from_secs(config.expire.max(1)),
        })
    }

    pub async fn get<T: DeserializeOwned>(&self, namespace: &str, key: &str) -> Option<T> {
        let mut conn = self.conn.clone();
        match conn
            .get::<_, Option<String>>(redis_key(namespace, key))
            .await
        {
            Ok(Some(raw)) => serde_json::from_str(&raw).ok(),
            Ok(None) => None,
            Err(error) => {
                tracing::warn!("读取 Redis 缓存失败: {error}");
                None
            }
        }
    }

    pub async fn put<T: Serialize>(&self, namespace: &str, key: &str, value: &T) {
        self.put_with_expire(namespace, key, value, self.default_expire)
            .await;
    }

    async fn put_with_expire<T: Serialize>(
        &self,
        namespace: &str,
        key: &str,
        value: &T,
        expire: Duration,
    ) {
        let Ok(raw) = serde_json::to_string(value) else {
            return;
        };
        let mut conn = self.conn.clone();
        if let Err(error) = conn
            .set_ex::<_, _, ()>(redis_key(namespace, key), raw, expire.as_secs().max(1))
            .await
        {
            tracing::warn!("写入 Redis 缓存失败: {error}");
        }
    }

    /// 命名空间整体失效；列表与鉴权用户缓存变更后调用。
    pub async fn invalidate(&self, namespace: &str) {
        self.delete_pattern(&format!("{PREFIX}:{namespace}:*"))
            .await;
    }

    pub async fn delete(&self, namespace: &str, key: &str) {
        let mut conn = self.conn.clone();
        if let Err(error) = conn.del::<_, ()>(redis_key(namespace, key)).await {
            tracing::warn!("删除 Redis 缓存失败: {error}");
        }
    }

    /// 清空本应用前缀下的全部缓存，测试初始化时使用。
    pub async fn clear(&self) {
        self.delete_pattern(&format!("{PREFIX}:*")).await;
    }

    pub async fn has_flag(&self, namespace: &str, key: &str) -> bool {
        let mut conn = self.conn.clone();
        match redis::cmd("EXISTS")
            .arg(redis_key(namespace, key))
            .query_async::<i64>(&mut conn)
            .await
        {
            Ok(count) => count > 0,
            Err(error) => {
                tracing::warn!("读取 Redis 缓存失败: {error}");
                false
            }
        }
    }

    pub async fn set_flag(&self, namespace: &str, key: &str, expire: Duration) {
        let mut conn = self.conn.clone();
        if let Err(error) = conn
            .set_ex::<_, _, ()>(redis_key(namespace, key), "1", expire.as_secs().max(1))
            .await
        {
            tracing::warn!("写入 Redis 缓存失败: {error}");
        }
    }

    async fn delete_pattern(&self, pattern: &str) {
        let mut conn = self.conn.clone();
        let mut cursor = 0u64;
        loop {
            let result: redis::RedisResult<(u64, Vec<String>)> = redis::cmd("SCAN")
                .arg(cursor)
                .arg("MATCH")
                .arg(pattern)
                .arg("COUNT")
                .arg(500)
                .query_async(&mut conn)
                .await;
            let (next, keys) = match result {
                Ok(value) => value,
                Err(error) => {
                    tracing::warn!("清理 Redis 缓存失败: {error}");
                    return;
                }
            };
            if !keys.is_empty()
                && let Err(error) = conn.del::<_, ()>(&keys).await
            {
                tracing::warn!("删除 Redis 缓存失败: {error}");
                return;
            }
            if next == 0 {
                return;
            }
            cursor = next;
        }
    }
}

/// 缓存值是独立的一份数据，key 保持可读，按 `eac:{namespace}:{key}` 组织。
fn redis_key(namespace: &str, key: &str) -> String {
    format!("{PREFIX}:{namespace}:{key}")
}

fn encode(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for byte in value.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(byte as char)
            }
            _ => out.push_str(&format!("%{byte:02X}")),
        }
    }
    out
}
