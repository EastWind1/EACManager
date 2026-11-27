use std::collections::HashMap;

use sha2::{Digest, Sha256};

use crate::bill::model::{AssigneeDto, Bill, BillDto, BillQueryParam, base_dto, full_dto};
use crate::bill::relation;
use crate::bill::store;
use crate::error::{AppError, AppResult};
use crate::model::PageResult;
use crate::state::AppState;

pub const DATA_ALL_AUTH_CODE: &str = "serviceBill:data-all";
pub const ASSIGN_AUTH_CODE: &str = "serviceBill:assign";
pub const QUERY_CACHE: &str = "bill-query";

/// 数据权限范围：None 表示可查看全部，Some(id) 表示只能访问指派给该用户的单据
pub async fn scope(app: &AppState) -> AppResult<Option<i32>> {
    let user = crate::auth::require_user()?;
    if crate::security::has_permission(app, &user, DATA_ALL_AUTH_CODE).await? {
        return Ok(None);
    }
    Ok(Some(user.id))
}

pub fn current_user_id() -> AppResult<i32> {
    crate::auth::current_user_id().ok_or_else(|| AppError::Unauthorized("未登录".into()))
}

pub async fn can_assign(app: &AppState) -> AppResult<bool> {
    let user = crate::auth::require_user()?;
    crate::security::has_permission(app, &user, ASSIGN_AUTH_CODE).await
}

async fn check_access(app: &AppState, scope: Option<i32>, bill_id: i32) -> AppResult<()> {
    let Some(user_id) = scope else { return Ok(()) };
    if store::is_assigned_to(&app.db, bill_id, user_id).await? {
        return Ok(());
    }
    Err(AppError::Biz("无权访问该服务单".into()))
}

/// 动作服务按 id 读写前使用：加载实体并校验数据权限
pub async fn require_by_id(app: &AppState, id: i32) -> AppResult<Bill> {
    if id == 0 {
        return Err(AppError::Biz("ID不能为空".into()));
    }
    let Some(bill) = store::find_by_id(&app.db, id).await? else {
        return Err(AppError::Biz("单据不存在".into()));
    };
    let scope = scope(app).await?;
    check_access(app, scope, bill.id).await?;
    Ok(bill)
}

pub async fn find_by_id(app: &AppState, id: i32) -> AppResult<BillDto> {
    if id == 0 {
        return Err(AppError::Biz("ID不能为空".into()));
    }
    let scope = scope(app).await?;
    let Some(bill) = store::find_full_by_id(&app.db, id).await? else {
        return Err(AppError::Biz("单据不存在".into()));
    };
    check_access(app, scope, bill.id).await?;
    let bill_id = bill.id;
    let attachments = relation::find_attachment_dtos(app, bill_id).await?;
    let assignees = find_assignees(app, bill_id).await?;
    Ok(full_dto(bill, attachments, assignees))
}

pub async fn find_by_param(
    app: &AppState,
    param: BillQueryParam,
) -> AppResult<PageResult<BillDto>> {
    param.page.validate().map_err(AppError::Biz)?;
    let order = param.page.order_by().map_err(AppError::Biz)?;
    let scope = scope(app).await?;
    // 数据权限与查询参数一起进缓存键，避免不同用户或不同条件复用结果。
    let key = query_cache_key(scope, &param);
    if let Some(cached) = app.cache.get(QUERY_CACHE, &key).await {
        return Ok(cached);
    }
    let BillQueryParam {
        number,
        states,
        project_name,
        assignee_id,
        order_start_date,
        order_end_date,
        processed_start_date,
        processed_end_date,
        page,
    } = param;
    let query = store::ListQuery {
        number: number.filter(|value| !value.trim().is_empty()),
        state_codes: states
            .unwrap_or_default()
            .iter()
            .map(|state| state.code())
            .collect(),
        project_name: project_name.filter(|value| !value.trim().is_empty()),
        order_start: order_start_date
            .as_deref()
            .and_then(|value| crate::util::parse_bound(value, false)),
        order_end: order_end_date
            .as_deref()
            .and_then(|value| crate::util::parse_bound(value, true)),
        processed_start: processed_start_date
            .as_deref()
            .and_then(|value| crate::util::parse_bound(value, false)),
        processed_end: processed_end_date
            .as_deref()
            .and_then(|value| crate::util::parse_bound(value, true)),
        scope_user: scope,
        assignee_id: assignee_id.filter(|id| *id != 0),
    };
    let page = store::find_page(&app.db, &query, &order, &page).await?;
    let bill_ids: Vec<i32> = page.items.iter().map(|bill| bill.id).collect();
    let mut assignees = load_assignees(app, &bill_ids).await?;
    let items = page
        .items
        .into_iter()
        .map(|bill| {
            let id = bill.id;
            let mut dto = base_dto(bill);
            dto.assignees = assignees.remove(&id).unwrap_or_default();
            dto
        })
        .collect();
    let result = PageResult {
        items,
        total_count: page.total_count,
        total_pages: page.total_pages,
        page_size: page.page_size,
        page_index: page.page_index,
    };
    app.cache.put(QUERY_CACHE, &key, &result).await;
    Ok(result)
}

pub async fn find_all_for_export(app: &AppState, ids: &[i32]) -> AppResult<Vec<Bill>> {
    if ids.is_empty() {
        return Err(AppError::Biz("ID 不能为空".into()));
    }
    let scope = scope(app).await?;
    let bills = store::find_all_with_details(&app.db, ids, scope).await?;
    let unique: std::collections::HashSet<i32> = ids.iter().copied().collect();
    if bills.is_empty() || bills.len() != unique.len() {
        return Err(AppError::Biz("单据不存在或无权访问".into()));
    }
    Ok(bills)
}

pub async fn find_assignees(app: &AppState, bill_id: i32) -> AppResult<Vec<AssigneeDto>> {
    let mut map = load_assignees(app, &[bill_id]).await?;
    Ok(map.remove(&bill_id).unwrap_or_default())
}

async fn load_assignees(
    app: &AppState,
    bill_ids: &[i32],
) -> AppResult<HashMap<i32, Vec<AssigneeDto>>> {
    let mut grouped: HashMap<i32, Vec<AssigneeDto>> = HashMap::new();
    let rows = store::find_assignees_by_bill_ids(&app.db, bill_ids).await?;
    if rows.is_empty() {
        return Ok(grouped);
    }
    let mut user_ids: Vec<i32> = rows.iter().map(|row| row.user_id).collect();
    user_ids.sort_unstable();
    user_ids.dedup();
    let users = crate::user::service::find_by_ids(&app.db, &user_ids).await?;
    let by_id: HashMap<i32, AssigneeDto> = users
        .into_iter()
        .map(|user| {
            (
                user.id,
                AssigneeDto {
                    id: user.id,
                    username: user.username,
                    name: user.name,
                },
            )
        })
        .collect();
    for row in &rows {
        if let Some(dto) = by_id.get(&row.user_id) {
            grouped
                .entry(row.service_bill_id)
                .or_default()
                .push(dto.clone());
        }
    }
    Ok(grouped)
}

/// 把数据权限和查询参数一起摘要成缓存键，避免跨用户或跨条件复用列表。
fn query_cache_key(scope: Option<i32>, param: &BillQueryParam) -> String {
    let scope_key = match scope {
        Some(user_id) => format!("user:{user_id}"),
        None => "all".to_string(),
    };
    let raw = serde_json::to_vec(param).unwrap_or_default();
    let digest = Sha256::digest(&raw);
    let hash: String = digest.iter().map(|byte| format!("{byte:02x}")).collect();
    format!("{scope_key}:{hash}")
}

/// 指派候选：全部启用用户，不要求用户管理权限
pub async fn list_assignees(app: &AppState) -> AppResult<Vec<AssigneeDto>> {
    let users = crate::user::service::find_enabled_users(&app.db).await?;
    Ok(users
        .into_iter()
        .map(|user| AssigneeDto {
            id: user.id,
            username: user.username,
            name: user.name,
        })
        .collect())
}
