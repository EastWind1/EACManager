use crate::error::{AppError, AppResult};
use crate::model::PageResult;
use crate::reimburse::model::{Reimburse, ReimburseDto, ReimburseQueryParam, base_dto, full_dto};
use crate::reimburse::relation;
use crate::reimburse::store;
use crate::state::AppState;

/// 报销单没有数据权限维度：列表、详情、导出对所有人一致
pub async fn find_by_id(app: &AppState, id: i32) -> AppResult<ReimburseDto> {
    if id == 0 {
        return Err(AppError::Biz("ID不能为空".into()));
    }
    let Some(entity) = store::find_full_by_id(&app.db, id).await? else {
        return Err(AppError::Biz("单据不存在".into()));
    };
    let entity_id = entity.id;
    let attachments = relation::find_attachment_dtos(app, entity_id).await?;
    Ok(full_dto(entity, attachments))
}

/// 动作服务按 id 读写前使用：加载实体，不存在即报错
pub async fn require_by_id(app: &AppState, id: i32) -> AppResult<Reimburse> {
    if id == 0 {
        return Err(AppError::Biz("ID不能为空".into()));
    }
    let Some(entity) = store::find_by_id(&app.db, id).await? else {
        return Err(AppError::Biz("单据不存在".into()));
    };
    Ok(entity)
}

pub async fn find_by_param(
    app: &AppState,
    param: ReimburseQueryParam,
) -> AppResult<PageResult<ReimburseDto>> {
    let ReimburseQueryParam {
        number,
        summary,
        states,
        reimburse_start_date,
        reimburse_end_date,
        page,
    } = param;
    page.validate().map_err(AppError::Biz)?;
    let order = page.order_by().map_err(AppError::Biz)?;
    let query = store::ListQuery {
        number: number.filter(|value| !value.trim().is_empty()),
        summary: summary.filter(|value| !value.trim().is_empty()),
        state_codes: states
            .unwrap_or_default()
            .iter()
            .map(|state| state.code())
            .collect(),
        start: reimburse_start_date
            .as_deref()
            .and_then(|value| crate::util::parse_bound(value, false)),
        end: reimburse_end_date
            .as_deref()
            .and_then(|value| crate::util::parse_bound(value, true)),
    };
    let page = store::find_page(&app.db, &query, &order, &page).await?;
    Ok(PageResult {
        items: page.items.into_iter().map(base_dto).collect(),
        total_count: page.total_count,
        total_pages: page.total_pages,
        page_size: page.page_size,
        page_index: page.page_index,
    })
}

pub async fn find_all_for_export(app: &AppState, ids: &[i32]) -> AppResult<Vec<Reimburse>> {
    if ids.is_empty() {
        return Err(AppError::Biz("ID 不能为空".into()));
    }
    let entities = store::find_all_with_details(&app.db, ids).await?;
    let unique: std::collections::HashSet<i32> = ids.iter().copied().collect();
    if entities.is_empty() || entities.len() != unique.len() {
        return Err(AppError::Biz("单据不存在".into()));
    }
    Ok(entities)
}
