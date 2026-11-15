use crate::error::AppResult;
use crate::model::{CountByState, MonthSumAmount};
use crate::reimburse::model::ReimburseState;
use crate::reimburse::store;
use crate::state::AppState;

const STAT_NAMESPACE: &str = "reimburse";

/// 统计口径与 Go 一致：新建/处理中/完成三个状态
pub async fn count_by_state(app: &AppState) -> AppResult<CountByState> {
    store::count_by_state(
        &app.db,
        &[
            ReimburseState::Created,
            ReimburseState::Processing,
            ReimburseState::Finished,
        ],
    )
    .await
}

pub async fn total_amount_group_by_month(app: &AppState) -> AppResult<Vec<MonthSumAmount>> {
    let key = "totalAmountGroupByMonth";
    if let Some(cached) = app.cache.get(STAT_NAMESPACE, key) {
        return Ok(cached);
    }
    let rows = store::total_amount_group_by_month(&app.db).await?;
    app.cache.put(STAT_NAMESPACE, key, &rows);
    Ok(rows)
}
