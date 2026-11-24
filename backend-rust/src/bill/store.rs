use chrono::{DateTime, Utc};
use sqlx::FromRow;
use sqlx::{Postgres, QueryBuilder};

use crate::bill::model::{Bill, BillDetail, BillState, BillType};
use crate::db::{Db, current_operator_id, map_db_error};
use crate::error::{AppError, AppResult};
use crate::model::{CountByState, MonthSumAmount, PageResult, QueryParam};

#[derive(Debug, Clone, FromRow)]
pub struct BillRow {
    pub id: i32,
    pub version: Option<i32>,
    pub number: Option<String>,
    pub bill_type: Option<i16>,
    pub state: Option<i16>,
    pub product_company_id: Option<i32>,
    pub project_name: Option<String>,
    pub project_address: Option<String>,
    pub project_contact: Option<String>,
    pub project_contact_phone: Option<String>,
    pub on_site_contact: Option<String>,
    pub on_site_phone: Option<String>,
    pub elevator_info: Option<String>,
    pub total_amount: Option<f64>,
    pub order_date: Option<DateTime<Utc>>,
    pub processed_date: Option<DateTime<Utc>>,
    pub finished_date: Option<DateTime<Utc>>,
    pub remark: Option<String>,
}

#[derive(Debug, Clone, FromRow)]
pub struct DetailRow {
    pub id: i32,
    pub service_bill_id: Option<i32>,
    pub device: Option<String>,
    pub quantity: Option<f64>,
    pub unit_price: Option<f64>,
    pub subtotal: Option<f64>,
    pub remark: Option<String>,
}

#[derive(Debug, Clone, FromRow)]
pub struct AssigneeRow {
    pub service_bill_id: i32,
    pub user_id: i32,
}

// NUMERIC 不能被 sqlx 解成 f64，金额列统一在 SQL 里转成 float8
const BILL_COLUMNS: &str = "id, number, type AS bill_type, state, product_company_id, project_name, \
     project_address, project_contact, project_contact_phone, on_site_contact, on_site_phone, \
     elevator_info, total_amount::float8 AS total_amount, order_date, processed_date, finished_date, \
     remark, version";

/// 行只用于构造一次实体，字段直接移动，不做无谓复制
fn to_bill(row: BillRow) -> Bill {
    Bill {
        id: row.id,
        version: row.version.unwrap_or(0),
        number: row.number.unwrap_or_default(),
        bill_type: BillType::from_code(row.bill_type.unwrap_or(0)),
        state: BillState::from_code(row.state.unwrap_or(0)),
        product_company_id: row.product_company_id,
        project_name: row.project_name.unwrap_or_default(),
        project_address: row.project_address.unwrap_or_default(),
        project_contact: row.project_contact.unwrap_or_default(),
        project_contact_phone: row.project_contact_phone.unwrap_or_default(),
        on_site_contact: row.on_site_contact.unwrap_or_default(),
        on_site_phone: row.on_site_phone.unwrap_or_default(),
        elevator_info: row.elevator_info.unwrap_or_default(),
        total_amount: row.total_amount.unwrap_or_default(),
        order_date: row.order_date,
        processed_date: row.processed_date,
        finished_date: row.finished_date,
        remark: row.remark.unwrap_or_default(),
        details: Vec::new(),
        product_company: None,
    }
}

fn detail_from_row(row: DetailRow) -> BillDetail {
    BillDetail {
        id: row.id,
        bill_id: row.service_bill_id.unwrap_or(0),
        device: row.device.unwrap_or_default(),
        quantity: row.quantity.unwrap_or_default(),
        unit_price: row.unit_price.unwrap_or_default(),
        subtotal: row.subtotal.unwrap_or_default(),
        remark: row.remark.unwrap_or_default(),
    }
}

/// 只取状态流转需要的主表字段
pub async fn find_by_id(db: &Db, id: i32) -> AppResult<Option<Bill>> {
    let row = sqlx::query_as::<_, BillRow>(safe_sql!(
        "SELECT {BILL_COLUMNS} FROM service_bill WHERE id = $1"
    ))
    .bind(id)
    .fetch_optional(db.pool())
    .await?;
    Ok(row.map(to_bill))
}

/// 预加载明细与产品公司
pub async fn find_full_by_id(db: &Db, id: i32) -> AppResult<Option<Bill>> {
    let Some(mut bill) = find_by_id(db, id).await? else {
        return Ok(None);
    };
    let mut details = find_details_by_bill_ids(db, &[id]).await?;
    bill.details = details.remove(&id).unwrap_or_default();
    if let Some(company_id) = bill.product_company_id {
        let companies = crate::company::store::find_by_ids(db, &[company_id]).await?;
        bill.product_company = companies.into_iter().next().map(Into::into);
    }
    Ok(Some(bill))
}

pub async fn find_details_by_bill_ids(
    db: &Db,
    bill_ids: &[i32],
) -> AppResult<std::collections::HashMap<i32, Vec<BillDetail>>> {
    let mut grouped = std::collections::HashMap::new();
    if bill_ids.is_empty() {
        return Ok(grouped);
    }
    let rows = sqlx::query_as::<_, DetailRow>(
        "SELECT id, service_bill_id, device, quantity::float8 AS quantity, \
         unit_price::float8 AS unit_price, subtotal::float8 AS subtotal, remark \
         FROM service_bill_detail WHERE service_bill_id = ANY($1) ORDER BY id",
    )
    .bind(bill_ids)
    .fetch_all(db.pool())
    .await?;
    for row in rows {
        let detail = detail_from_row(row);
        grouped
            .entry(detail.bill_id)
            .or_insert_with(Vec::new)
            .push(detail);
    }
    Ok(grouped)
}

/// 主表与明细必须一起落库，因此连接由服务层传入
pub async fn insert(conn: &mut sqlx::PgConnection, bill: &Bill) -> AppResult<i32> {
    let operator = current_operator_id();
    let id: i32 = sqlx::query_scalar(
        "INSERT INTO service_bill (number, type, state, product_company_id, project_name, \
         project_address, project_contact, project_contact_phone, on_site_contact, on_site_phone, \
         elevator_info, total_amount, order_date, processed_date, finished_date, remark, version, \
         created_date, last_modified_date, created_by_id, last_modified_by_id) \
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, 0, \
         now(), now(), $17, $17) RETURNING id",
    )
    .bind(&bill.number)
    .bind(bill.bill_type.code())
    .bind(bill.state.code())
    .bind(bill.product_company_id)
    .bind(&bill.project_name)
    .bind(&bill.project_address)
    .bind(&bill.project_contact)
    .bind(&bill.project_contact_phone)
    .bind(&bill.on_site_contact)
    .bind(&bill.on_site_phone)
    .bind(&bill.elevator_info)
    .bind(bill.total_amount)
    .bind(bill.order_date)
    .bind(bill.processed_date)
    .bind(bill.finished_date)
    .bind(&bill.remark)
    .bind(operator)
    .fetch_one(&mut *conn)
    .await
    .map_err(|error| map_db_error(error, "单据编号已存在"))?;

    for detail in &bill.details {
        insert_detail(conn, id, detail).await?;
    }
    Ok(id)
}

async fn insert_detail(
    conn: &mut sqlx::PgConnection,
    bill_id: i32,
    detail: &BillDetail,
) -> AppResult<i32> {
    let id: i32 = sqlx::query_scalar(
        "INSERT INTO service_bill_detail (service_bill_id, device, quantity, unit_price, subtotal, remark) \
         VALUES ($1, $2, $3, $4, $5, $6) RETURNING id",
    )
    .bind(bill_id)
    .bind(&detail.device)
    .bind(detail.quantity)
    .bind(detail.unit_price)
    .bind(detail.subtotal)
    .bind(&detail.remark)
    .fetch_one(&mut *conn)
    .await?;
    Ok(id)
}

/// 更新主表字段并同步明细（按 id 对账：保留行原地更新、移除行真删除、新增行插入）
pub async fn update(conn: &mut sqlx::PgConnection, bill: &Bill) -> AppResult<()> {
    let operator = current_operator_id();
    let affected = sqlx::query(
        "UPDATE service_bill SET number = $1, type = $2, state = $3, total_amount = $4, \
         project_name = $5, project_address = $6, project_contact = $7, project_contact_phone = $8, \
         on_site_contact = $9, on_site_phone = $10, elevator_info = $11, order_date = $12, \
         processed_date = $13, finished_date = $14, remark = $15, product_company_id = $16, \
         last_modified_date = now(), last_modified_by_id = $17, version = COALESCE(version, 0) + 1 \
         WHERE id = $18 AND COALESCE(version, 0) = $19",
    )
    .bind(&bill.number)
    .bind(bill.bill_type.code())
    .bind(bill.state.code())
    .bind(bill.total_amount)
    .bind(&bill.project_name)
    .bind(&bill.project_address)
    .bind(&bill.project_contact)
    .bind(&bill.project_contact_phone)
    .bind(&bill.on_site_contact)
    .bind(&bill.on_site_phone)
    .bind(&bill.elevator_info)
    .bind(bill.order_date)
    .bind(bill.processed_date)
    .bind(bill.finished_date)
    .bind(&bill.remark)
    .bind(bill.product_company_id)
    .bind(operator)
    .bind(bill.id)
    .bind(bill.version)
    .execute(&mut *conn)
    .await
    .map_err(|error| map_db_error(error, "单据编号已存在"))?
    .rows_affected();
    if affected == 0 {
        return Err(AppError::Biz("数据已被更改，稍后重试".into()));
    }
    sync_details(conn, bill).await
}

async fn sync_details(conn: &mut sqlx::PgConnection, bill: &Bill) -> AppResult<()> {
    let existing: Vec<i32> =
        sqlx::query_scalar("SELECT id FROM service_bill_detail WHERE service_bill_id = $1")
            .bind(bill.id)
            .fetch_all(&mut *conn)
            .await?;
    let mut keep = std::collections::HashSet::new();
    for detail in &bill.details {
        if detail.id == 0 {
            insert_detail(conn, bill.id, detail).await?;
            continue;
        }
        keep.insert(detail.id);
        sqlx::query(
            "UPDATE service_bill_detail SET service_bill_id = $1, device = $2, quantity = $3, \
             unit_price = $4, subtotal = $5, remark = $6 WHERE id = $7",
        )
        .bind(bill.id)
        .bind(&detail.device)
        .bind(detail.quantity)
        .bind(detail.unit_price)
        .bind(detail.subtotal)
        .bind(&detail.remark)
        .bind(detail.id)
        .execute(&mut *conn)
        .await?;
    }
    let remove: Vec<i32> = existing
        .into_iter()
        .filter(|id| !keep.contains(id))
        .collect();
    if !remove.is_empty() {
        sqlx::query("DELETE FROM service_bill_detail WHERE id = ANY($1)")
            .bind(&remove)
            .execute(&mut *conn)
            .await?;
    }
    Ok(())
}

pub async fn update_state(conn: &mut sqlx::PgConnection, bill: &Bill) -> AppResult<()> {
    let operator = current_operator_id();
    let affected = sqlx::query(
        "UPDATE service_bill SET state = $1, processed_date = $2, finished_date = $3, \
         last_modified_date = now(), last_modified_by_id = $4, version = COALESCE(version, 0) + 1 \
         WHERE id = $5 AND COALESCE(version, 0) = $6",
    )
    .bind(bill.state.code())
    .bind(bill.processed_date)
    .bind(bill.finished_date)
    .bind(operator)
    .bind(bill.id)
    .bind(bill.version)
    .execute(&mut *conn)
    .await?
    .rows_affected();
    if affected == 0 {
        return Err(AppError::Biz("数据已被更改，稍后重试".into()));
    }
    Ok(())
}

/// 整体替换一张单据的负责人；user_ids 为空表示清空指派
pub async fn replace_assignees(
    conn: &mut sqlx::PgConnection,
    bill_id: i32,
    user_ids: &[i32],
) -> AppResult<()> {
    let operator = current_operator_id();
    sqlx::query("DELETE FROM service_bill_assignee WHERE service_bill_id = $1")
        .bind(bill_id)
        .execute(&mut *conn)
        .await?;
    if !user_ids.is_empty() {
        sqlx::query(
            "INSERT INTO service_bill_assignee (service_bill_id, user_id, created_date, \
             last_modified_date, created_by_id, last_modified_by_id) \
             SELECT $1, unnest($2::int[]), now(), now(), $3, $3",
        )
        .bind(bill_id)
        .bind(user_ids)
        .bind(operator)
        .execute(&mut *conn)
        .await
        .map_err(|error| map_db_error(error, "负责人不能重复"))?;
    }
    Ok(())
}

pub async fn find_assignees_by_bill_ids(db: &Db, bill_ids: &[i32]) -> AppResult<Vec<AssigneeRow>> {
    if bill_ids.is_empty() {
        return Ok(Vec::new());
    }
    Ok(sqlx::query_as::<_, AssigneeRow>(
        "SELECT service_bill_id, user_id FROM service_bill_assignee \
         WHERE service_bill_id = ANY($1) ORDER BY service_bill_id, user_id",
    )
    .bind(bill_ids)
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_assignee_ids(db: &Db, bill_id: i32) -> AppResult<Vec<i32>> {
    Ok(sqlx::query_scalar(
        "SELECT user_id FROM service_bill_assignee WHERE service_bill_id = $1 ORDER BY user_id",
    )
    .bind(bill_id)
    .fetch_all(db.pool())
    .await?)
}

pub async fn is_assigned_to(db: &Db, bill_id: i32, user_id: i32) -> AppResult<bool> {
    let count: i64 = sqlx::query_scalar(
        "SELECT count(1) FROM service_bill_assignee WHERE service_bill_id = $1 AND user_id = $2",
    )
    .bind(bill_id)
    .bind(user_id)
    .fetch_one(db.pool())
    .await?;
    Ok(count > 0)
}

pub async fn delete(conn: &mut sqlx::PgConnection, id: i32) -> AppResult<bool> {
    sqlx::query("DELETE FROM service_bill_assignee WHERE service_bill_id = $1")
        .bind(id)
        .execute(&mut *conn)
        .await?;
    let affected = sqlx::query("DELETE FROM service_bill WHERE id = $1")
        .bind(id)
        .execute(&mut *conn)
        .await?
        .rows_affected();
    Ok(affected > 0)
}

/// 列表查询条件
pub struct ListQuery {
    pub number: Option<String>,
    pub state_codes: Vec<i16>,
    pub project_name: Option<String>,
    pub order_start: Option<DateTime<Utc>>,
    pub order_end: Option<DateTime<Utc>>,
    pub processed_start: Option<DateTime<Utc>>,
    pub processed_end: Option<DateTime<Utc>>,
    /// None 表示可查看全部；Some(id) 只能看指派给该用户的单据
    pub scope_user: Option<i32>,
    pub assignee_id: Option<i32>,
}

/// 动态条件只往 QueryBuilder 里 push_bind 值，SQL 片段全部来自字面量。
///
/// 计数与取页两条语句共用同一份条件，因此这里只借用条件、不消费：
/// 字符串字段以引用绑定，避免为了复用而复制单号与项目名。
fn bind_filters(builder: &mut QueryBuilder<Postgres>, query: &ListQuery) {
    if let Some(number) = &query.number {
        builder.push(" AND number = ").push_bind(number);
    }
    if let Some(user_id) = query.scope_user.or(query.assignee_id) {
        builder
            .push(" AND id IN (SELECT service_bill_id FROM service_bill_assignee WHERE user_id = ")
            .push_bind(user_id)
            .push(")");
    }
    if !query.state_codes.is_empty() {
        builder
            .push(" AND state = ANY(")
            .push_bind(&query.state_codes)
            .push(")");
    }
    if let Some(project_name) = &query.project_name {
        builder
            .push(" AND project_name LIKE ")
            .push_bind(format!("%{project_name}%"));
    }
    bind_range(builder, "order_date", query.order_start, ">=");
    bind_range(builder, "order_date", query.order_end, "<=");
    bind_range(builder, "processed_date", query.processed_start, ">=");
    bind_range(builder, "processed_date", query.processed_end, "<=");
}

fn bind_range(
    builder: &mut QueryBuilder<Postgres>,
    column: &str,
    value: Option<DateTime<Utc>>,
    operator: &str,
) {
    if let Some(value) = value {
        builder
            .push(" AND ")
            .push(column)
            .push(' ')
            .push(operator)
            .push(' ')
            .push_bind(value);
    }
}

pub async fn find_page(
    db: &Db,
    query: &ListQuery,
    order: &str,
    page: &QueryParam,
) -> AppResult<PageResult<Bill>> {
    let mut count = QueryBuilder::new("SELECT count(1) FROM service_bill WHERE 1 = 1");
    bind_filters(&mut count, query);
    let total: i64 = count.build_query_scalar().fetch_one(db.pool()).await?;
    if total == 0 {
        return Ok(PageResult::empty(page));
    }

    let mut select: QueryBuilder<Postgres> = QueryBuilder::new(format!(
        "SELECT {BILL_COLUMNS} FROM service_bill WHERE 1 = 1"
    ));
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
        .build_query_as::<BillRow>()
        .fetch_all(db.pool())
        .await?;
    let bills: Vec<Bill> = rows.into_iter().map(to_bill).collect();
    Ok(PageResult::new(bills, total, page))
}

/// 批量导出：主表、明细、公司各一次查询
pub async fn find_all_with_details(
    db: &Db,
    ids: &[i32],
    scope_user: Option<i32>,
) -> AppResult<Vec<Bill>> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    let rows = match scope_user {
        Some(user_id) => {
            sqlx::query_as::<_, BillRow>(safe_sql!(
                "SELECT {BILL_COLUMNS} FROM service_bill WHERE id = ANY($1) AND id IN \
                 (SELECT service_bill_id FROM service_bill_assignee WHERE user_id = $2) ORDER BY id"
            ))
            .bind(ids)
            .bind(user_id)
            .fetch_all(db.pool())
            .await?
        }
        None => {
            sqlx::query_as::<_, BillRow>(safe_sql!(
                "SELECT {BILL_COLUMNS} FROM service_bill WHERE id = ANY($1) ORDER BY id"
            ))
            .bind(ids)
            .fetch_all(db.pool())
            .await?
        }
    };
    let mut bills: Vec<Bill> = rows.into_iter().map(to_bill).collect();
    if bills.is_empty() {
        return Ok(bills);
    }
    let bill_ids: Vec<i32> = bills.iter().map(|bill| bill.id).collect();
    let company_ids: Vec<i32> = bills
        .iter()
        .filter_map(|bill| bill.product_company_id)
        .collect();
    let mut details = find_details_by_bill_ids(db, &bill_ids).await?;
    let companies: std::collections::HashMap<i32, _> =
        crate::company::store::find_by_ids(db, &company_ids)
            .await?
            .into_iter()
            .map(|row| (row.id, crate::company::model::CompanyDto::from(row)))
            .collect();
    for bill in &mut bills {
        bill.details = details.remove(&bill.id).unwrap_or_default();
        if let Some(company_id) = bill.product_company_id {
            bill.product_company = companies.get(&company_id).cloned();
        }
    }
    Ok(bills)
}
/// 按状态计数：返回固定状态集合，缺省为 0（前端按 key 取值）
pub async fn count_by_state(
    db: &Db,
    states: &[BillState],
    scope_user: Option<i32>,
) -> AppResult<CountByState> {
    let codes: Vec<i16> = states.iter().map(|state| state.code()).collect();
    let rows = match scope_user {
        Some(user_id) => {
            sqlx::query_as::<_, (i16, i64)>(
                "SELECT state, count(1) FROM service_bill WHERE state = ANY($1) AND id IN \
                 (SELECT service_bill_id FROM service_bill_assignee WHERE user_id = $2) \
                 GROUP BY state",
            )
            .bind(&codes)
            .bind(user_id)
            .fetch_all(db.pool())
            .await?
        }
        None => {
            sqlx::query_as::<_, (i16, i64)>(
                "SELECT state, count(1) FROM service_bill WHERE state = ANY($1) GROUP BY state",
            )
            .bind(&codes)
            .fetch_all(db.pool())
            .await?
        }
    };
    let mut result: CountByState = states
        .iter()
        .map(|state| (state.as_str().to_string(), 0))
        .collect();
    for (code, count) in rows {
        result.insert(BillState::from_code(code).as_str().to_string(), count);
    }
    Ok(result)
}

/// 近一年已处理/已完成单据的月度金额合计
pub async fn total_amount_group_by_month(
    db: &Db,
    scope_user: Option<i32>,
) -> AppResult<Vec<MonthSumAmount>> {
    let codes = [BillState::Processed.code(), BillState::Finished.code()];
    let rows = match scope_user {
        Some(user_id) => {
            sqlx::query_as::<_, (String, f64)>(
                "SELECT to_char(processed_date, 'YYYY-MM') AS month, sum(total_amount)::float8 AS amount \
                 FROM service_bill WHERE state = ANY($1) AND processed_date IS NOT NULL \
                 AND processed_date >= date_trunc('month', now()) - interval '11 months' \
                 AND processed_date < date_trunc('month', now()) + interval '1 month' \
                 AND id IN (SELECT service_bill_id FROM service_bill_assignee WHERE user_id = $2) \
                 GROUP BY 1 ORDER BY 1",
            )
            .bind(codes)
            .bind(user_id)
            .fetch_all(db.pool())
            .await?
        }
        None => {
            sqlx::query_as::<_, (String, f64)>(
                "SELECT to_char(processed_date, 'YYYY-MM') AS month, sum(total_amount)::float8 AS amount \
                 FROM service_bill WHERE state = ANY($1) AND processed_date IS NOT NULL \
                 AND processed_date >= date_trunc('month', now()) - interval '11 months' \
                 AND processed_date < date_trunc('month', now()) + interval '1 month' \
                 GROUP BY 1 ORDER BY 1",
            )
            .bind(codes)
            .fetch_all(db.pool())
            .await?
        }
    };
    Ok(rows
        .into_iter()
        .map(|(month, amount)| MonthSumAmount { month, amount })
        .collect())
}
