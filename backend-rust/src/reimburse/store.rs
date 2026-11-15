use std::collections::{HashMap, HashSet};

use chrono::{DateTime, Utc};
use sqlx::{FromRow, Postgres, QueryBuilder};

use crate::db::{Db, current_operator_id, map_db_error};
use crate::error::{AppError, AppResult};
use crate::model::{CountByState, MonthSumAmount, PageResult, QueryParam};
use crate::reimburse::model::{Reimburse, ReimburseDetail, ReimburseState};

#[derive(Debug, Clone, FromRow)]
pub struct ReimburseRow {
    pub id: i32,
    pub number: Option<String>,
    pub state: Option<i16>,
    pub summary: Option<String>,
    pub total_amount: Option<f64>,
    pub reimburse_date: Option<DateTime<Utc>>,
    pub remark: Option<String>,
}

#[derive(Debug, Clone, FromRow)]
pub struct DetailRow {
    pub id: i32,
    pub reimbursement_id: Option<i32>,
    pub name: Option<String>,
    pub amount: Option<f64>,
}

// NUMERIC 不能被 sqlx 解成 f64，金额列统一在 SQL 里转成 float8
const COLUMNS: &str = "id, number, state, summary, total_amount::float8 AS total_amount, \
     reimburse_date, remark";

/// 行只用于构造一次实体，字段直接移动，不做无谓复制
fn to_entity(row: ReimburseRow) -> Reimburse {
    Reimburse {
        id: row.id,
        number: row.number.unwrap_or_default(),
        state: ReimburseState::from_code(row.state.unwrap_or(0)),
        summary: row.summary.unwrap_or_default(),
        total_amount: row.total_amount.unwrap_or_default(),
        reimburse_date: row.reimburse_date,
        remark: row.remark.unwrap_or_default(),
        details: Vec::new(),
    }
}

fn detail_from_row(row: DetailRow) -> ReimburseDetail {
    ReimburseDetail {
        id: row.id,
        reimburse_id: row.reimbursement_id.unwrap_or(0),
        name: row.name.unwrap_or_default(),
        amount: row.amount.unwrap_or_default(),
    }
}

pub async fn find_by_id(db: &Db, id: i32) -> AppResult<Option<Reimburse>> {
    let row = sqlx::query_as::<_, ReimburseRow>(&format!(
        "SELECT {COLUMNS} FROM reimbursement WHERE id = $1"
    ))
    .bind(id)
    .fetch_optional(db.pool())
    .await?;
    Ok(row.map(to_entity))
}

pub async fn find_full_by_id(db: &Db, id: i32) -> AppResult<Option<Reimburse>> {
    let Some(mut entity) = find_by_id(db, id).await? else {
        return Ok(None);
    };
    let mut details = find_details_by_entity_ids(db, &[id]).await?;
    entity.details = details.remove(&id).unwrap_or_default();
    Ok(Some(entity))
}

pub async fn find_details_by_entity_ids(
    db: &Db,
    ids: &[i32],
) -> AppResult<HashMap<i32, Vec<ReimburseDetail>>> {
    let mut grouped = HashMap::new();
    if ids.is_empty() {
        return Ok(grouped);
    }
    let rows = sqlx::query_as::<_, DetailRow>(
        "SELECT id, reimbursement_id, name, amount::float8 AS amount FROM reimburse_detail \
         WHERE reimbursement_id = ANY($1) ORDER BY id",
    )
    .bind(ids)
    .fetch_all(db.pool())
    .await?;
    for row in rows {
        let detail = detail_from_row(row);
        grouped
            .entry(detail.reimburse_id)
            .or_insert_with(Vec::new)
            .push(detail);
    }
    Ok(grouped)
}

/// 主表与明细必须一起落库，因此连接由服务层传入
pub async fn insert(conn: &mut sqlx::PgConnection, entity: &Reimburse) -> AppResult<i32> {
    let operator = current_operator_id();
    let id: i32 = sqlx::query_scalar(
        "INSERT INTO reimbursement (number, state, summary, total_amount, reimburse_date, remark, \
         version, created_date, last_modified_date, created_by_id, last_modified_by_id) \
         VALUES ($1, $2, $3, $4, $5, $6, 0, now(), now(), $7, $7) RETURNING id",
    )
    .bind(&entity.number)
    .bind(entity.state.code())
    .bind(&entity.summary)
    .bind(entity.total_amount)
    .bind(entity.reimburse_date)
    .bind(&entity.remark)
    .bind(operator)
    .fetch_one(&mut *conn)
    .await
    .map_err(|error| map_db_error(error, "单据编号已存在"))?;
    for detail in &entity.details {
        insert_detail(conn, id, detail).await?;
    }
    Ok(id)
}

async fn insert_detail(
    conn: &mut sqlx::PgConnection,
    reimburse_id: i32,
    detail: &ReimburseDetail,
) -> AppResult<i32> {
    Ok(sqlx::query_scalar(
        "INSERT INTO reimburse_detail (reimbursement_id, name, amount) VALUES ($1, $2, $3) RETURNING id",
    )
    .bind(reimburse_id)
    .bind(&detail.name)
    .bind(detail.amount)
    .fetch_one(&mut *conn)
    .await?)
}

/// 更新主表字段并同步明细（按 id 对账：保留行原地更新、移除行真删除、新增行插入）
pub async fn update(conn: &mut sqlx::PgConnection, entity: &Reimburse) -> AppResult<()> {
    let operator = current_operator_id();
    let affected = sqlx::query(
        "UPDATE reimbursement SET number = $1, state = $2, summary = $3, total_amount = $4, \
         reimburse_date = $5, remark = $6, last_modified_date = now(), last_modified_by_id = $7 \
         WHERE id = $8",
    )
    .bind(&entity.number)
    .bind(entity.state.code())
    .bind(&entity.summary)
    .bind(entity.total_amount)
    .bind(entity.reimburse_date)
    .bind(&entity.remark)
    .bind(operator)
    .bind(entity.id)
    .execute(&mut *conn)
    .await
    .map_err(|error| map_db_error(error, "单据编号已存在"))?
    .rows_affected();
    if affected == 0 {
        return Err(AppError::Biz("数据已被更改，稍后重试".into()));
    }
    sync_details(conn, entity).await
}

async fn sync_details(conn: &mut sqlx::PgConnection, entity: &Reimburse) -> AppResult<()> {
    let existing: Vec<i32> =
        sqlx::query_scalar("SELECT id FROM reimburse_detail WHERE reimbursement_id = $1")
            .bind(entity.id)
            .fetch_all(&mut *conn)
            .await?;
    let mut keep = HashSet::new();
    for detail in &entity.details {
        if detail.id == 0 {
            insert_detail(conn, entity.id, detail).await?;
            continue;
        }
        keep.insert(detail.id);
        sqlx::query(
            "UPDATE reimburse_detail SET reimbursement_id = $1, name = $2, amount = $3 WHERE id = $4",
        )
        .bind(entity.id)
        .bind(&detail.name)
        .bind(detail.amount)
        .bind(detail.id)
        .execute(&mut *conn)
        .await?;
    }
    let remove: Vec<i32> = existing
        .into_iter()
        .filter(|id| !keep.contains(id))
        .collect();
    if !remove.is_empty() {
        sqlx::query("DELETE FROM reimburse_detail WHERE id = ANY($1)")
            .bind(&remove)
            .execute(&mut *conn)
            .await?;
    }
    Ok(())
}

pub async fn update_state(conn: &mut sqlx::PgConnection, entity: &Reimburse) -> AppResult<()> {
    let operator = current_operator_id();
    let affected = sqlx::query(
        "UPDATE reimbursement SET state = $1, last_modified_date = now(), last_modified_by_id = $2 \
         WHERE id = $3",
    )
    .bind(entity.state.code())
    .bind(operator)
    .bind(entity.id)
    .execute(&mut *conn)
    .await?
    .rows_affected();
    if affected == 0 {
        return Err(AppError::Biz("数据已被更改，稍后重试".into()));
    }
    Ok(())
}

pub async fn delete(conn: &mut sqlx::PgConnection, id: i32) -> AppResult<bool> {
    let affected = sqlx::query("DELETE FROM reimbursement WHERE id = $1")
        .bind(id)
        .execute(&mut *conn)
        .await?
        .rows_affected();
    Ok(affected > 0)
}

/// 列表查询条件
pub struct ListQuery {
    pub number: Option<String>,
    pub summary: Option<String>,
    pub state_codes: Vec<i16>,
    pub start: Option<DateTime<Utc>>,
    pub end: Option<DateTime<Utc>>,
}

/// 动态条件只往 QueryBuilder 里 push_bind 值，SQL 片段全部来自字面量。
///
/// 计数与取页两条语句共用同一份条件，因此这里只借用条件、不消费：
/// 字符串字段以引用绑定，避免为了复用而复制单号与摘要。
fn bind_filters<'q>(builder: &mut QueryBuilder<'q, Postgres>, query: &'q ListQuery) {
    if let Some(number) = &query.number {
        builder.push(" AND number = ").push_bind(number);
    }
    if let Some(summary) = &query.summary {
        builder
            .push(" AND summary LIKE ")
            .push_bind(format!("%{summary}%"));
    }
    if !query.state_codes.is_empty() {
        builder
            .push(" AND state = ANY(")
            .push_bind(&query.state_codes)
            .push(")");
    }
    if let Some(value) = query.start {
        builder.push(" AND reimburse_date >= ").push_bind(value);
    }
    if let Some(value) = query.end {
        builder.push(" AND reimburse_date <= ").push_bind(value);
    }
}

pub async fn find_page(
    db: &Db,
    query: &ListQuery,
    order: &str,
    page: &QueryParam,
) -> AppResult<PageResult<Reimburse>> {
    let mut count = QueryBuilder::new("SELECT count(1) FROM reimbursement WHERE 1 = 1");
    bind_filters(&mut count, query);
    let total: i64 = count.build_query_scalar().fetch_one(db.pool()).await?;
    if total == 0 {
        return Ok(PageResult::empty(page));
    }

    let mut select: QueryBuilder<'_, Postgres> =
        QueryBuilder::new(format!("SELECT {COLUMNS} FROM reimbursement WHERE 1 = 1"));
    bind_filters(&mut select, query);
    select.push(" ORDER BY ");
    if order.is_empty() {
        select.push("id DESC");
    } else {
        select.push(order);
    }
    if page.has_page() {
        select
            .push(" LIMIT ")
            .push(page.page_size())
            .push(" OFFSET ")
            .push(page.offset());
    }
    let rows = select
        .build_query_as::<ReimburseRow>()
        .fetch_all(db.pool())
        .await?;
    let items: Vec<Reimburse> = rows.into_iter().map(to_entity).collect();
    Ok(PageResult::new(items, total, page))
}

/// 批量导出：主表、明细各一次查询
pub async fn find_all_with_details(db: &Db, ids: &[i32]) -> AppResult<Vec<Reimburse>> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    let rows = sqlx::query_as::<_, ReimburseRow>(&format!(
        "SELECT {COLUMNS} FROM reimbursement WHERE id = ANY($1) ORDER BY id"
    ))
    .bind(ids)
    .fetch_all(db.pool())
    .await?;
    let mut entities: Vec<Reimburse> = rows.into_iter().map(to_entity).collect();
    if entities.is_empty() {
        return Ok(entities);
    }
    let entity_ids: Vec<i32> = entities.iter().map(|entity| entity.id).collect();
    let mut details = find_details_by_entity_ids(db, &entity_ids).await?;
    for entity in &mut entities {
        entity.details = details.remove(&entity.id).unwrap_or_default();
    }
    Ok(entities)
}

/// 按状态计数：返回固定状态集合，缺省为 0
pub async fn count_by_state(db: &Db, states: &[ReimburseState]) -> AppResult<CountByState> {
    let codes: Vec<i16> = states.iter().map(|state| state.code()).collect();
    let rows = sqlx::query_as::<_, (i16, i64)>(
        "SELECT state, count(1) FROM reimbursement WHERE state = ANY($1) GROUP BY state",
    )
    .bind(&codes)
    .fetch_all(db.pool())
    .await?;
    let mut result: CountByState = states
        .iter()
        .map(|state| (state.as_str().to_string(), 0))
        .collect();
    for (code, count) in rows {
        result.insert(ReimburseState::from_code(code).as_str().to_string(), count);
    }
    Ok(result)
}

/// 近一年报销单的月度金额合计
pub async fn total_amount_group_by_month(db: &Db) -> AppResult<Vec<MonthSumAmount>> {
    let rows = sqlx::query_as::<_, (String, f64)>(
        "SELECT to_char(reimburse_date, 'YYYY-MM') AS month, sum(total_amount)::float8 AS amount \
         FROM reimbursement WHERE reimburse_date IS NOT NULL \
         AND reimburse_date >= date_trunc('month', now()) - interval '11 months' \
         AND reimburse_date < date_trunc('month', now()) + interval '1 month' \
         GROUP BY 1 ORDER BY 1",
    )
    .fetch_all(db.pool())
    .await?;
    Ok(rows
        .into_iter()
        .map(|(month, amount)| MonthSumAmount { month, amount })
        .collect())
}
