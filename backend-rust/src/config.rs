use std::path::{Path, PathBuf};

use serde::Deserialize;

/// 内嵌默认配置，与 Go 侧 config.yaml 保持同一套键名。
const DEFAULT_YAML: &str = include_str!("../config/config.yaml");

#[derive(Debug, Clone, Default, Deserialize, serde::Serialize)]
#[serde(default)]
pub struct Config {
    pub server: ServerConfig,
    pub log: LogConfig,
    pub db: DatabaseConfig,
    pub cache: CacheConfig,
    pub jwt: JwtConfig,
    pub attachment: AttachConfig,
    pub ocr: OcrConfig,
}

#[derive(Debug, Clone, Deserialize, serde::Serialize)]
#[serde(default)]
pub struct ServerConfig {
    pub port: u16,
    pub mode: String,
    /// 为空表示不启用 CORS
    pub allowed_origins: Vec<String>,
}

#[derive(Debug, Clone, Deserialize, serde::Serialize)]
#[serde(default)]
pub struct LogConfig {
    pub level: String,
}

#[derive(Debug, Clone, Deserialize, serde::Serialize)]
#[serde(default)]
pub struct DatabaseConfig {
    pub host: String,
    pub port: u16,
    pub name: String,
    pub username: String,
    pub password: String,
    pub sslmode: String,
    pub max_open_conns: u32,
    pub max_idle_conns: u32,
    /// 单位分钟
    pub conn_max_lifetime: u64,
}

#[derive(Debug, Clone, Deserialize, serde::Serialize)]
#[serde(default)]
pub struct CacheConfig {
    /// 单位秒
    pub expire: u64,
}

#[derive(Debug, Clone, Deserialize, serde::Serialize)]
#[serde(default)]
pub struct JwtConfig {
    /// 不写默认值，必须由 JWT_SECRET 环境变量注入
    pub secret: String,
    pub expire: i64,
}

#[derive(Debug, Clone, Deserialize, serde::Serialize)]
#[serde(default)]
pub struct AttachConfig {
    pub path: String,
    pub max_file_size: usize,
}

#[derive(Debug, Clone, Default, Deserialize, serde::Serialize)]
#[serde(default)]
pub struct OcrConfig {
    /// 完整端点（含路径），不是主机名
    pub url: String,
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            port: 8080,
            mode: "release".into(),
            allowed_origins: Vec::new(),
        }
    }
}

impl Default for LogConfig {
    fn default() -> Self {
        Self {
            level: "info".into(),
        }
    }
}

impl Default for DatabaseConfig {
    fn default() -> Self {
        Self {
            host: "localhost".into(),
            port: 5432,
            name: String::new(),
            username: "postgres".into(),
            password: String::new(),
            sslmode: "disable".into(),
            max_open_conns: 20,
            max_idle_conns: 20,
            conn_max_lifetime: 60,
        }
    }
}

impl Default for CacheConfig {
    fn default() -> Self {
        Self { expire: 86_400 }
    }
}

impl Default for JwtConfig {
    fn default() -> Self {
        Self {
            secret: String::new(),
            expire: 604_800,
        }
    }
}

impl Default for AttachConfig {
    fn default() -> Self {
        Self {
            path: "attachment".into(),
            max_file_size: 5_242_880,
        }
    }
}

impl Config {
    /// 读取内嵌默认配置，再用工作目录下的 config.yaml 覆盖，最后应用环境变量。
    pub fn load() -> Result<Self, String> {
        let mut value = Self::parse_default()?;
        let local = find_local_config();
        if let Some(path) = &local {
            let text = std::fs::read_to_string(path)
                .map_err(|e| format!("读取配置文件失败 {}: {e}", path.display()))?;
            let overlay: yaml_serde::Value =
                yaml_serde::from_str(&text).map_err(|e| format!("解析配置文件失败: {e}"))?;
            let mut base = yaml_serde::to_value(&value).map_err(|e| e.to_string())?;
            merge_yaml(&mut base, overlay);
            value =
                yaml_serde::from_value(base).map_err(|e| format!("反序列化配置文件失败: {e}"))?;
        }
        apply_env(&mut value);
        if value.jwt.secret.len() < 32 {
            return Err("jwt.secret 未配置或不足 32 字节：请通过 JWT_SECRET 环境变量注入".into());
        }
        Ok(value)
    }

    fn parse_default() -> Result<Self, String> {
        yaml_serde::from_str(DEFAULT_YAML).map_err(|e| format!("加载内嵌配置文件失败：{e}"))
    }

    /// sqlx 连接串
    pub fn dsn(&self) -> String {
        let ssl = match self.db.sslmode.as_str() {
            "require" | "verify-ca" | "verify-full" => "require",
            "allow" => "prefer",
            _ => "disable",
        };
        format!(
            "postgres://{}:{}@{}:{}/{}?sslmode={ssl}",
            encode(&self.db.username),
            encode(&self.db.password),
            self.db.host,
            self.db.port,
            encode(&self.db.name)
        )
    }
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

fn find_local_config() -> Option<PathBuf> {
    const CANDIDATES: [&str; 5] = ["config", ".", "../config", "..", "../../config"];
    for dir in CANDIDATES {
        let path = Path::new(dir).join("config.yaml");
        if path.is_file() {
            return Some(path);
        }
    }
    None
}

fn merge_yaml(base: &mut yaml_serde::Value, overlay: yaml_serde::Value) {
    match (base, overlay) {
        (yaml_serde::Value::Mapping(base_map), yaml_serde::Value::Mapping(overlay_map)) => {
            for (key, value) in overlay_map {
                match base_map.get_mut(&key) {
                    Some(existing) => merge_yaml(existing, value),
                    None => {
                        base_map.insert(key, value);
                    }
                }
            }
        }
        (base, overlay) => *base = overlay,
    }
}

/// 环境变量优先于文件：DB_HOST / JWT_SECRET / SERVER_PORT ……（键名转大写下划线）
fn apply_env(config: &mut Config) {
    fn env(name: &str) -> Option<String> {
        std::env::var(name).ok().filter(|v| !v.is_empty())
    }
    if let Some(v) = env("SERVER_PORT").and_then(|v| v.parse().ok()) {
        config.server.port = v;
    }
    if let Some(v) = env("SERVER_MODE") {
        config.server.mode = v;
    }
    if let Some(v) = env("LOG_LEVEL") {
        config.log.level = v;
    }
    if let Some(v) = env("DB_HOST") {
        config.db.host = v;
    }
    if let Some(v) = env("DB_PORT").and_then(|v| v.parse().ok()) {
        config.db.port = v;
    }
    if let Some(v) = env("DB_NAME") {
        config.db.name = v;
    }
    if let Some(v) = env("DB_USERNAME") {
        config.db.username = v;
    }
    if let Some(v) = env("DB_PASSWORD") {
        config.db.password = v;
    }
    if let Some(v) = env("DB_SSLMODE") {
        config.db.sslmode = v;
    }
    if let Some(v) = env("JWT_SECRET") {
        config.jwt.secret = v;
    }
    if let Some(v) = env("JWT_EXPIRE").and_then(|v| v.parse().ok()) {
        config.jwt.expire = v;
    }
    if let Some(v) = env("ATTACHMENT_PATH") {
        config.attachment.path = v;
    }
    if let Some(v) = env("OCR_URL") {
        config.ocr.url = v;
    }
}
