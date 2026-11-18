use sqlx::FromRow;

use crate::db::{Db, current_operator_id, map_db_error};
use crate::error::{AppError, AppResult};

use super::model::CompanyDto;

#[derive(Debug, Clone, FromRow)]
pub struct CompanyRow {
    pub id: i32,
    pub name: String,
    pub contact_name: Option<String>,
    pub contact_phone: Option<String>,
    pub email: Option<String>,
    pub address: Option<String>,
    pub disabled: Option<bool>,
}

const COLUMNS: &str = "id, name, contact_name, contact_phone, email, address, disabled";

pub async fn count_enabled(db: &Db) -> AppResult<i64> {
    Ok(
        sqlx::query_scalar("SELECT count(1) FROM company WHERE disabled = false")
            .fetch_one(db.pool())
            .await?,
    )
}

pub async fn find_enabled_page(
    db: &Db,
    order: &str,
    limit: i64,
    offset: i64,
) -> AppResult<Vec<CompanyRow>> {
    let order_clause = if order.is_empty() {
        "ORDER BY id".to_string()
    } else {
        format!("ORDER BY {order}")
    };
    let page = if limit > 0 {
        format!("LIMIT {limit} OFFSET {offset}")
    } else {
        String::new()
    };
    Ok(sqlx::query_as::<_, CompanyRow>(safe_sql!(
        "SELECT {COLUMNS} FROM company WHERE disabled = false {order_clause} {page}"
    ))
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_enabled_by_name_contains(db: &Db, name: &str) -> AppResult<Vec<CompanyRow>> {
    Ok(sqlx::query_as::<_, CompanyRow>(safe_sql!(
        "SELECT {COLUMNS} FROM company WHERE disabled = false AND name LIKE $1 ORDER BY id"
    ))
    .bind(format!("%{name}%"))
    .fetch_all(db.pool())
    .await?)
}

pub async fn create(
    db: &Db,
    name: &str,
    contact_name: &str,
    contact_phone: &str,
    email: &str,
    address: &str,
) -> AppResult<CompanyRow> {
    let operator = current_operator_id();
    sqlx::query_as::<_, CompanyRow>(safe_sql!(
        "INSERT INTO company (name, contact_name, contact_phone, email, address, disabled, \
         created_date, last_modified_date, created_by_id, last_modified_by_id) \
         VALUES ($1, $2, $3, $4, $5, false, now(), now(), $6, $6) RETURNING {COLUMNS}"
    ))
    .bind(name)
    .bind(contact_name)
    .bind(contact_phone)
    .bind(email)
    .bind(address)
    .bind(operator)
    .fetch_one(db.pool())
    .await
    .map_err(|error| map_db_error(error, "公司名称已存在"))
}

pub async fn update(
    db: &Db,
    id: i32,
    name: &str,
    contact_name: &str,
    contact_phone: &str,
    email: &str,
    address: &str,
) -> AppResult<()> {
    let operator = current_operator_id();
    let affected = sqlx::query(
        "UPDATE company SET name = $1, contact_name = $2, contact_phone = $3, email = $4, \
         address = $5, last_modified_date = now(), last_modified_by_id = $6 WHERE id = $7",
    )
    .bind(name)
    .bind(contact_name)
    .bind(contact_phone)
    .bind(email)
    .bind(address)
    .bind(operator)
    .bind(id)
    .execute(db.pool())
    .await
    .map_err(|error| map_db_error(error, "公司名称已存在"))?
    .rows_affected();
    if affected == 0 {
        return Err(AppError::Biz("数据已被更改，稍后重试".into()));
    }
    Ok(())
}

pub async fn set_disabled(db: &Db, id: i32) -> AppResult<()> {
    let operator = current_operator_id();
    let affected = sqlx::query(
        "UPDATE company SET disabled = true, last_modified_date = now(), last_modified_by_id = $1 \
         WHERE id = $2",
    )
    .bind(operator)
    .bind(id)
    .execute(db.pool())
    .await?
    .rows_affected();
    if affected == 0 {
        return Err(AppError::Biz("公司不存在".into()));
    }
    Ok(())
}

pub async fn find_by_ids(db: &Db, ids: &[i32]) -> AppResult<Vec<CompanyRow>> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    Ok(sqlx::query_as::<_, CompanyRow>(safe_sql!(
        "SELECT {COLUMNS} FROM company WHERE id = ANY($1)"
    ))
    .bind(ids)
    .fetch_all(db.pool())
    .await?)
}

/// 行数据只用于构造一次响应时移动字段，避免再次复制公司名与联系方式
impl From<CompanyRow> for CompanyDto {
    fn from(row: CompanyRow) -> Self {
        Self {
            id: row.id,
            name: row.name,
            contact_name: row.contact_name.unwrap_or_default(),
            contact_phone: row.contact_phone.unwrap_or_default(),
            email: row.email.unwrap_or_default(),
            address: row.address.unwrap_or_default(),
            disabled: row.disabled,
        }
    }
}
