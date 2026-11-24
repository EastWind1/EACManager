use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::LazyLock;
use std::time::{Duration as StdDuration, SystemTime};

use chrono::{Duration, Utc};
use regex::Regex;
use sha2::{Digest, Sha256};
use uuid::Uuid;

use crate::attach::model::{AttachDto, AttachFile, AttachStatus};
use crate::attach::store;
use crate::attach::{files, resolve_root};
use crate::error::{AppError, AppResult};
use crate::state::AppState;

/// 游离附件保留时长，超过后由每日任务回收
pub const TRANSIENT_RETENTION_DAYS: i64 = 7;

static NAMESPACE_PATTERN: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^[a-zA-Z0-9_-]+$").expect("namespace regex"));

/// 上传的文件：原始文件名 + 内容
#[derive(Debug, Clone, serde::Deserialize)]
pub struct UploadFile {
    pub name: String,
    pub data: Vec<u8>,
}

/// 附件根目录
fn root(app: &AppState) -> AppResult<PathBuf> {
    resolve_root(&app.config.attachment.path)
}

/// 固定临时目录，与业务 namespace 隔离（namespace 不允许包含点）
pub fn temp_root(app: &AppState) -> AppResult<PathBuf> {
    let dir = root(app)?.join(".tmp");
    std::fs::create_dir_all(&dir)?;
    Ok(dir)
}

/// 绝对路径：由 relative_path 拼接根目录，并校验仍在根目录内
fn absolute_path(app: &AppState, relative_path: &str) -> AppResult<PathBuf> {
    if relative_path.is_empty() {
        return Err(AppError::Biz("相对路径为空".into()));
    }
    crate::util::safe_join(&root(app)?, relative_path)
}

pub async fn upload(
    app: &AppState,
    namespace: &str,
    files_in: Vec<UploadFile>,
) -> AppResult<Vec<AttachDto>> {
    if !NAMESPACE_PATTERN.is_match(namespace) {
        return Err(AppError::Biz("附件命名空间不合法".into()));
    }
    if files_in.is_empty() {
        return Err(AppError::Biz("文件不能为空".into()));
    }
    let mut result = Vec::with_capacity(files_in.len());
    for file in files_in {
        result.push(AttachDto::from(save_one(app, namespace, file).await?));
    }
    Ok(result)
}

async fn save_one(
    app: &AppState,
    namespace: &str,
    file: UploadFile,
) -> AppResult<crate::attach::store::AttachRow> {
    crate::util::valid_name(&file.name)?;

    // 落盘文件名使用服务端生成的 hash；原始文件名只作为 attachment.name 留在数据库
    let mut hasher = Sha256::new();
    hasher.update(Uuid::new_v4().to_string());
    let hash: String = hasher
        .finalize()
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect();
    let relative_path = format!("{namespace}/{hash}");
    let target = absolute_path(app, &relative_path)?;
    files::create_parent_dirs(&target)?;
    std::fs::write(&target, &file.data)?;
    let attach_type = match files::detect_type(&target) {
        Ok(attach_type) => attach_type,
        Err(error) => {
            files::delete_quietly(&target);
            return Err(error);
        }
    };
    match store::create(
        &app.db,
        &file.name,
        attach_type,
        &relative_path,
        AttachStatus::Transient,
    )
    .await
    {
        Ok(row) => Ok(row),
        Err(error) => {
            files::delete_quietly(&target);
            Err(error)
        }
    }
}

/// 把瞬时态附件置为持久态；任一 id 不合法或已提交则整体报错。
/// 在单据写事务内执行，连接由调用方传入。
pub async fn persist(conn: &mut sqlx::PgConnection, ids: &[i32]) -> AppResult<()> {
    let unique = crate::util::unique_ids(ids);
    if unique.is_empty() {
        return Ok(());
    }
    let rows = store::find_by_ids_conn(conn, &unique).await?;
    if rows.len() != unique.len() {
        return Err(AppError::Biz("附件不存在".into()));
    }
    for row in &rows {
        if row.status != AttachStatus::Transient.code() {
            return Err(AppError::Biz("附件不存在或已被提交".into()));
        }
        assert_can_access(row.status, row.created_by_id)?;
    }
    let affected = store::persist_by_ids(conn, &unique).await?;
    if affected != unique.len() as i64 {
        return Err(AppError::Biz("附件不存在或已被提交".into()));
    }
    Ok(())
}

/// 删除附件及其物理文件；在单据写事务内执行
pub async fn delete(app: &AppState, conn: &mut sqlx::PgConnection, ids: &[i32]) -> AppResult<()> {
    let unique = crate::util::unique_ids(ids);
    if unique.is_empty() {
        return Ok(());
    }
    let rows = store::find_by_ids_conn(conn, &unique).await?;
    if rows.len() != unique.len() {
        return Err(AppError::Biz("附件不存在".into()));
    }
    store::delete_by_ids(conn, &unique).await?;
    for row in &rows {
        let relative = row.relative_path.as_deref().unwrap_or_default();
        if let Ok(path) = absolute_path(app, relative) {
            files::delete_quietly(&path);
        }
    }
    Ok(())
}

/// 回收过期的游离附件
pub async fn clean_transient_files(app: &AppState) -> AppResult<()> {
    let cutoff = Utc::now() - Duration::days(TRANSIENT_RETENTION_DAYS);
    let expired = store::find_transient_before(&app.db, cutoff).await?;
    if expired.is_empty() {
        return Ok(());
    }
    let ids: Vec<i32> = expired.iter().map(|row| row.id).collect();
    store::delete_ids(&app.db, &ids).await?;
    for row in &expired {
        let relative = row.relative_path.as_deref().unwrap_or_default();
        if let Ok(path) = absolute_path(app, relative) {
            files::delete_quietly(&path);
        }
    }
    tracing::info!("回收游离附件 {} 个", ids.len());
    Ok(())
}

/// 批量读取附件的唯一入口：一次查询完成存在性、归属校验与路径解析。
pub async fn get_paths(app: &AppState, ids: &[i32]) -> AppResult<HashMap<i32, AttachFile>> {
    let unique = crate::util::unique_ids(ids);
    if unique.is_empty() {
        return Ok(HashMap::new());
    }
    let rows = store::find_by_ids(&app.db, &unique).await?;
    if rows.len() != unique.len() {
        return Err(AppError::Biz("附件不存在".into()));
    }
    let mut result = HashMap::with_capacity(rows.len());
    for row in rows {
        assert_can_access(row.status, row.created_by_id)?;
        let path = absolute_path(app, row.relative_path.as_deref().unwrap_or_default())?;
        if !path.exists() {
            return Err(AppError::Biz("文件不存在".into()));
        }
        result.insert(
            row.id,
            AttachFile {
                attachment: AttachDto::from(row),
                path,
            },
        );
    }
    Ok(result)
}

/// 读取单个附件。
pub async fn get_path(app: &AppState, id: i32) -> AppResult<AttachFile> {
    if id <= 0 {
        return Err(AppError::Biz("附件 ID 不合法".into()));
    }
    let mut files = get_paths(app, &[id]).await?;
    files
        .remove(&id)
        .ok_or_else(|| AppError::Biz("附件不存在".into()))
}

/// 把附件复制到目标目录，重名处理也留在附件模块内。
pub async fn copy_to(app: &AppState, ids: &[i32], target_dir: &Path) -> AppResult<()> {
    let files = get_paths(app, ids).await?;
    if files.is_empty() {
        return Ok(());
    }
    std::fs::create_dir_all(target_dir)?;
    let mut files: Vec<AttachFile> = files.into_values().collect();
    files.sort_by_key(|file| file.attachment.id);
    for file in files {
        let name = file.attachment.name.trim();
        let name = if name.is_empty() { "attachment" } else { name };
        let target = files::unique_target_path(target_dir, name)?;
        files::copy_file(&file.path, &target)?;
    }
    Ok(())
}

/// 打包临时目录，压缩包放在附件临时目录下。
pub fn zip_directory(app: &AppState, source_dir: &Path) -> AppResult<PathBuf> {
    let name = source_dir
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or("export");
    let target = temp_root(app)?.join(format!("{name}.zip"));
    files::zip_dir(source_dir, Some(&target))
}

/// 导出等一次性工作的临时目录，统一放在附件临时目录下。
pub fn scratch_dir(app: &AppState, prefix: &str) -> AppResult<PathBuf> {
    let path = temp_root(app)?.join(format!("{prefix}-{}", crate::util::gen_int_id()));
    std::fs::create_dir_all(&path)?;
    Ok(path)
}

/// 下载：返回 (文件名, 绝对路径)
pub async fn get_resource(
    app: &AppState,
    id: i32,
    fallback_name: &str,
) -> AppResult<(String, PathBuf)> {
    let file = get_path(app, id).await?;
    let name = if file.attachment.name.is_empty() {
        fallback_name.to_string()
    } else {
        file.attachment.name
    };
    Ok((name, file.path))
}

/// 未绑定单据的瞬时附件只允许上传者本人读取
fn assert_can_access(status: i16, created_by_id: Option<i32>) -> AppResult<()> {
    if status != AttachStatus::Transient.code() {
        return Ok(());
    }
    let Some(user) = crate::auth::current_user() else {
        return Err(AppError::Unauthorized("未登录".into()));
    };
    if let Some(created_by) = created_by_id.filter(|id| *id != 0)
        && created_by != user.id
    {
        return Err(AppError::Forbidden("无权访问该附件".into()));
    }
    Ok(())
}

/// 读取到内存，供导出等业务模块直接使用
pub fn read_bytes(path: &Path) -> AppResult<Vec<u8>> {
    std::fs::read(path).map_err(|error| AppError::FileOp(format!("读取文件失败: {error}")))
}

/// 回收临时目录中超过一天的文件与空目录。
pub fn clean_temp_files(app: &AppState) -> AppResult<()> {
    let dir = temp_root(app)?;
    let cutoff = SystemTime::now()
        .checked_sub(StdDuration::from_secs(24 * 60 * 60))
        .unwrap_or(SystemTime::UNIX_EPOCH);
    let mut dirs = Vec::new();
    let mut stack = vec![dir];
    while let Some(current) = stack.pop() {
        let Ok(entries) = std::fs::read_dir(&current) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            let Ok(meta) = entry.metadata() else {
                continue;
            };
            if meta.is_dir() {
                dirs.push(path.clone());
                stack.push(path);
            } else if meta.is_file() && meta.modified().is_ok_and(|time| time < cutoff) {
                files::delete_quietly(&path);
            }
        }
    }
    // 先删深层目录，父目录才有机会变空
    dirs.sort_by_key(|path| std::cmp::Reverse(path.components().count()));
    for path in dirs {
        let empty = std::fs::read_dir(&path)
            .map(|mut entries| entries.next().is_none())
            .unwrap_or(false);
        if empty {
            let _ = std::fs::remove_dir(&path);
        }
    }
    Ok(())
}

/// 启动后台清理任务：每 24 小时扫描一次，回收游离附件与临时文件
pub fn spawn_cleaner(app: AppState) {
    tokio::spawn(async move {
        let mut interval = tokio::time::interval(tokio::time::Duration::from_secs(24 * 60 * 60));
        loop {
            interval.tick().await;
            if let Err(e) = clean_transient_files(&app).await {
                tracing::error!("清理瞬时附件失败: {e}");
            }
            if let Err(e) = clean_temp_files(&app) {
                tracing::error!("清理临时文件失败: {e}");
            }
        }
    });
}
