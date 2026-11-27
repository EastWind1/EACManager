use crate::bill::model::BillState;
use crate::bill::query;
use crate::bill::store;
use crate::error::AppResult;
use crate::model::{CountByState, MonthSumAmount};
use crate::state::AppState;

/// 金额统计缓存；写入不主动失效，按默认 TTL 刷新。
pub const AMOUNT_CACHE: &str = "bill-amount";

/// 统计口径与 Go 一致：新建/处理中/处理完成三个状态
pub async fn count_by_state(app: &AppState) -> AppResult<CountByState> {
    let scope = query::scope(app).await?;
    store::count_by_state(
        &app.db,
        &[
            BillState::Created,
            BillState::Processing,
            BillState::Processed,
        ],
        scope,
    )
    .await
}

pub async fn total_amount_group_by_month(app: &AppState) -> AppResult<Vec<MonthSumAmount>> {
    let scope = query::scope(app).await?;
    // 数据权限决定缓存键，避免把一个人的统计结果给到另一个人
    let key = match scope {
        Some(user_id) => format!("totalAmountGroupByMonth:user:{user_id}"),
        None => "totalAmountGroupByMonth".to_string(),
    };
    if let Some(cached) = app.cache.get(AMOUNT_CACHE, &key).await {
        return Ok(cached);
    }
    let rows = store::total_amount_group_by_month(&app.db, scope).await?;
    app.cache.put(AMOUNT_CACHE, &key, &rows).await;
    Ok(rows)
}
