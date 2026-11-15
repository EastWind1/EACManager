use chrono::{DateTime, Utc};
use sqlx::FromRow;

use crate::attach::model::{AttachDto, AttachStatus, AttachType};
use crate::db::{Db, current_operator_id};
use crate::error::AppResult;

#[derive(Debug, Clone, FromRow)]
pub struct AttachRow {
    pub id: i32,
    pub name: Option<String>,
    pub attach_type: Option<i16>,
    pub relative_path: Option<String>,
    pub status: i16,
    pub created_by_id: Option<i32>,
}

/// 行数据只用于构造一次响应时直接移动文件名
impl From<AttachRow> for AttachDto {
    fn from(row: AttachRow) -> Self {
        Self {
            id: row.id,
            name: row.name.unwrap_or_default(),
            attach_type: AttachType::from_code(row.attach_type.unwrap_or(4)),
        }
    }
}

const COLUMNS: &str = "id, name, type AS attach_type, relative_path, status, created_by_id";

pub async fn find_by_id(db: &Db, id: i32) -> AppResult<Option<AttachRow>> {
    Ok(
        sqlx::query_as::<_, AttachRow>(&format!("SELECT {COLUMNS} FROM attachment WHERE id = $1"))
            .bind(id)
            .fetch_optional(db.pool())
            .await?,
    )
}

pub async fn find_by_ids(db: &Db, ids: &[i32]) -> AppResult<Vec<AttachRow>> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    Ok(sqlx::query_as::<_, AttachRow>(&format!(
        "SELECT {COLUMNS} FROM attachment WHERE id = ANY($1) ORDER BY id"
    ))
    .bind(ids)
    .fetch_all(db.pool())
    .await?)
}

pub async fn create(
    db: &Db,
    name: &str,
    attach_type: AttachType,
    relative_path: &str,
    status: AttachStatus,
) -> AppResult<AttachRow> {
    let operator = current_operator_id();
    Ok(sqlx::query_as::<_, AttachRow>(&format!(
        "INSERT INTO attachment (name, type, relative_path, status, created_date, last_modified_date, \
         created_by_id, last_modified_by_id) VALUES ($1, $2, $3, $4, now(), now(), $5, $5) \
         RETURNING {COLUMNS}"
    ))
    .bind(name)
    .bind(attach_type.code())
    .bind(relative_path)
    .bind(status.code())
    .bind(operator)
    .fetch_one(db.pool())
    .await?)
}

/// 把瞬时态附件一次性置为持久态；返回真正被更新的行数。
/// 只更新仍是瞬时态的行，重复提交会因此被调用方识别出来。
pub async fn persist_by_ids(conn: &mut sqlx::PgConnection, ids: &[i32]) -> AppResult<i64> {
    if ids.is_empty() {
        return Ok(0);
    }
    let operator = current_operator_id();
    let affected = sqlx::query(
        "UPDATE attachment SET status = $1, last_modified_date = now(), last_modified_by_id = $2 \
         WHERE id = ANY($3) AND status = $4",
    )
    .bind(AttachStatus::Persistent.code())
    .bind(operator)
    .bind(ids)
    .bind(AttachStatus::Transient.code())
    .execute(&mut *conn)
    .await?
    .rows_affected();
    Ok(affected as i64)
}

pub async fn delete_by_ids(conn: &mut sqlx::PgConnection, ids: &[i32]) -> AppResult<()> {
    if ids.is_empty() {
        return Ok(());
    }
    sqlx::query("DELETE FROM attachment WHERE id = ANY($1)")
        .bind(ids)
        .execute(&mut *conn)
        .await?;
    Ok(())
}

/// 事务外按 id 删除（回收任务用）
pub async fn delete_ids(db: &Db, ids: &[i32]) -> AppResult<()> {
    if ids.is_empty() {
        return Ok(());
    }
    sqlx::query("DELETE FROM attachment WHERE id = ANY($1)")
        .bind(ids)
        .execute(db.pool())
        .await?;
    Ok(())
}

/// 事务内按 id 读取附件（持久化前校验状态用）
pub async fn find_by_ids_conn(
    conn: &mut sqlx::PgConnection,
    ids: &[i32],
) -> AppResult<Vec<AttachRow>> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    Ok(sqlx::query_as::<_, AttachRow>(&format!(
        "SELECT {COLUMNS} FROM attachment WHERE id = ANY($1) ORDER BY id"
    ))
    .bind(ids)
    .fetch_all(&mut *conn)
    .await?)
}

pub async fn find_transient_before(db: &Db, cutoff: DateTime<Utc>) -> AppResult<Vec<AttachRow>> {
    Ok(sqlx::query_as::<_, AttachRow>(&format!(
        "SELECT {COLUMNS} FROM attachment WHERE status = $1 AND created_date < $2 ORDER BY id"
    ))
    .bind(AttachStatus::Transient.code())
    .bind(cutoff)
    .fetch_all(db.pool())
    .await?)
}
