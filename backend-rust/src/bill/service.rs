use chrono::{DateTime, Utc};

use crate::bill::model::{AssignParam, Bill, BillDto, BillRequest, BillState, full_dto};
use crate::bill::{mapper, query, relation, store};
use crate::error::{AppError, AppResult};
use crate::model::{ActionRow, ActionsResult};
use crate::state::AppState;

pub const ATTACH_NAMESPACE: &str = "SERVICE_BILL";

pub fn generate_number() -> String {
    format!("S{}", crate::util::gen_int_id())
}

/// 明细小计必须等于数量×单价，总金额必须等于各明细之和
pub fn validate_amount(bill: &Bill) -> AppResult<()> {
    let mut total: f64 = 0.0;
    for detail in &bill.details {
        let expected = detail.quantity * detail.unit_price;
        if (detail.subtotal - expected).abs() > 0.01 {
            return Err(AppError::Biz("明细金额有误".into()));
        }
        total += detail.subtotal;
    }
    if (total - bill.total_amount).abs() > 0.01 {
        return Err(AppError::Biz("总金额有误".into()));
    }
    Ok(())
}

/// 显式提交的产品公司必须存在；未提交表示不关联。
async fn validate_company(app: &AppState, bill: &Bill) -> AppResult<()> {
    let Some(company_id) = bill.product_company_id else {
        return Ok(());
    };
    if crate::company::service::find_by_id(&app.db, company_id)
        .await?
        .is_none()
    {
        return Err(AppError::Biz("公司不存在".into()));
    }
    Ok(())
}

pub async fn create(app: &AppState, mut request: BillRequest) -> AppResult<BillDto> {
    request.validate()?;
    if request.id != 0 {
        return Err(AppError::Biz("单据 ID 自动生成".into()));
    }
    let requested_assignees = request.assignees.take().unwrap_or_default();
    let (mut bill, attachments) = request.into_parts();
    validate_amount(&bill)?;
    validate_company(app, &bill).await?;
    let current_id = query::current_user_id()?;

    let mut assignee_ids: Vec<i32> = requested_assignees
        .iter()
        .filter(|assignee| assignee.id != 0)
        .map(|assignee| assignee.id)
        .collect();
    assignee_ids = crate::util::unique_ids(&assignee_ids);
    if assignee_ids.is_empty() {
        assignee_ids = vec![current_id];
    } else {
        assignee_ids = resolve_user_ids(app, &assignee_ids).await?;
        let assigned_to_self = assignee_ids.len() == 1 && assignee_ids[0] == current_id;
        if !assigned_to_self && !query::can_assign(app).await? {
            return Err(AppError::Biz("无权限指派服务单".into()));
        }
    }
    bill.state = BillState::Created;

    if bill.number.is_empty() {
        bill.number = generate_number();
    } else {
        crate::util::valid_number(&bill.number)?;
    }

    let id = app
        .db
        .transaction(async |conn| {
            let id = store::insert(conn, &bill).await?;
            relation::update_attachments(app, conn, id, Some(&attachments)).await?;
            store::replace_assignees(conn, id, &assignee_ids).await?;
            Ok(id)
        })
        .await?;

    evict_query(app).await;
    load_full(app, id).await
}

pub async fn update(app: &AppState, mut request: BillRequest) -> AppResult<BillDto> {
    request.validate()?;
    if request.id == 0 {
        return Err(AppError::Biz("单据 ID 为空".into()));
    }
    let requested_assignees = request.assignees.take();
    let (mut bill, attachments) = request.into_parts();
    validate_amount(&bill)?;
    validate_company(app, &bill).await?;

    app.db
        .transaction(async |conn| {
            let existing = query::require_by_id(app, bill.id).await?;
            if bill.number != existing.number {
                crate::util::valid_number(&bill.number)?;
            }
            bill.version = existing.version;
            let mut replace_assignees: Option<Vec<i32>> = None;
            if let Some(assignees) = requested_assignees.as_deref() {
                let requested: Vec<i32> = assignees
                    .iter()
                    .filter(|assignee| assignee.id != 0)
                    .map(|assignee| assignee.id)
                    .collect();
                let requested = resolve_user_ids(app, &requested).await?;
                let current = store::find_assignee_ids(&app.db, bill.id).await?;
                if !crate::util::same_ids(&current, &requested) {
                    if !existing.state.is_assignable() {
                        return Err(AppError::Biz("只有新建或处理中的单据才能指派".into()));
                    }
                    if !query::can_assign(app).await? {
                        return Err(AppError::Biz("无权限指派服务单".into()));
                    }
                    replace_assignees = Some(requested);
                }
            }
            bill.state = existing.state;
            store::update(conn, &bill).await?;
            if let Some(ids) = replace_assignees {
                store::replace_assignees(conn, bill.id, &ids).await?;
            }
            relation::update_attachments(app, conn, bill.id, Some(&attachments)).await?;
            Ok(())
        })
        .await?;

    evict_query(app).await;
    load_full(app, bill.id).await
}

pub async fn delete(app: &AppState, ids: &[i32]) -> AppResult<ActionsResult<i32>> {
    if ids.is_empty() {
        return Err(AppError::Biz("ID 为空".into()));
    }
    let rows = for_each_id(ids, |id| async move {
        app.db
            .transaction(async |conn| {
                let bill = query::require_by_id(app, id).await?;
                if bill.state != BillState::Created {
                    return Err(AppError::Biz("非创建状态不能删除".into()));
                }
                relation::update_attachments(app, conn, bill.id, None).await?;
                if !store::delete(conn, bill.id).await? {
                    return Err(AppError::Biz("数据已被更改，稍后重试".into()));
                }
                Ok(())
            })
            .await
    })
    .await;
    evict_query(app).await;
    Ok(rows)
}

async fn transition<F>(app: &AppState, ids: &[i32], apply: F) -> AppResult<ActionsResult<i32>>
where
    F: Fn(&mut Bill) -> AppResult<()> + Copy,
{
    if ids.is_empty() {
        return Err(AppError::Biz("ID 为空".into()));
    }
    let rows = for_each_id(ids, |id| async move {
        app.db
            .transaction(async |conn| {
                let mut bill = query::require_by_id(app, id).await?;
                apply(&mut bill)?;
                store::update_state(conn, &bill).await
            })
            .await
    })
    .await;
    evict_query(app).await;
    Ok(rows)
}

pub async fn process(app: &AppState, ids: &[i32]) -> AppResult<ActionsResult<i32>> {
    transition(app, ids, |bill| {
        bill.transition_from(
            BillState::Created,
            BillState::Processing,
            "非创建状态的单据不能处理",
        )
    })
    .await
}

pub async fn processed(
    app: &AppState,
    ids: &[i32],
    processed_date: Option<DateTime<Utc>>,
) -> AppResult<ActionsResult<i32>> {
    let date = processed_date.unwrap_or_else(crate::util::now);
    transition(app, ids, move |bill| {
        bill.transition_from(
            BillState::Processing,
            BillState::Processed,
            "非处理中状态的单据不能处理完成",
        )?;
        bill.processed_date = Some(date);
        Ok(())
    })
    .await
}

pub async fn finish(
    app: &AppState,
    ids: &[i32],
    finished_date: Option<DateTime<Utc>>,
) -> AppResult<ActionsResult<i32>> {
    let date = finished_date.unwrap_or_else(crate::util::now);
    transition(app, ids, move |bill| {
        bill.transition_from(
            BillState::Processed,
            BillState::Finished,
            "非处理完成状态的单据不能完成",
        )?;
        bill.finished_date = Some(date);
        Ok(())
    })
    .await
}

pub async fn cancel_process(app: &AppState, ids: &[i32]) -> AppResult<ActionsResult<i32>> {
    transition(app, ids, |bill| {
        bill.transition_from(
            BillState::Processing,
            BillState::Created,
            "非处理中状态的单据不能取消处理",
        )?;
        bill.processed_date = None;
        Ok(())
    })
    .await
}

pub async fn cancel_processed(app: &AppState, ids: &[i32]) -> AppResult<ActionsResult<i32>> {
    transition(app, ids, |bill| {
        bill.transition_from(
            BillState::Processed,
            BillState::Processing,
            "非处理完成状态的单据不能取消处理完成",
        )?;
        bill.processed_date = None;
        Ok(())
    })
    .await
}

pub async fn cancel_finish(app: &AppState, ids: &[i32]) -> AppResult<ActionsResult<i32>> {
    transition(app, ids, |bill| {
        bill.transition_from(
            BillState::Finished,
            BillState::Processed,
            "非完成状态的单据不能取消完成",
        )?;
        bill.finished_date = None;
        Ok(())
    })
    .await
}

/// 整体替换单据负责人；assignee_ids 为空表示取消指派
pub async fn assign(app: &AppState, param: &AssignParam) -> AppResult<ActionsResult<i32>> {
    if param.ids.is_empty() {
        return Err(AppError::Biz("ID 为空".into()));
    }
    let assignee_ids = resolve_user_ids(app, &param.assignee_ids).await?;
    let mut rows = Vec::with_capacity(param.ids.len());
    for id in param.ids.iter().copied() {
        let result = app
            .db
            .transaction(async |conn| {
                let bill = query::require_by_id(app, id).await?;
                if !bill.state.is_assignable() {
                    return Err(AppError::Biz("只有新建或处理中的单据才能指派".into()));
                }
                store::replace_assignees(conn, bill.id, &assignee_ids).await
            })
            .await;
        rows.push(action_row(id, result));
    }
    evict_query(app).await;
    Ok(ActionsResult::new(rows))
}

/// 上传并解析文件生成单据 DTO，不落库
pub async fn generate_by_file(app: &AppState, name: String, data: Vec<u8>) -> AppResult<BillDto> {
    let attachments = crate::attach::service::upload(
        app,
        ATTACH_NAMESPACE,
        vec![crate::attach::service::UploadFile { name, data }],
    )
    .await?;
    let attachment = attachments
        .first()
        .cloned()
        .ok_or_else(|| AppError::Biz("附件为空".into()))?;
    let mut bill = mapper::map(app, &attachment).await?;
    bill.attachments = attachments;
    Ok(bill)
}

pub async fn export(app: &AppState, ids: &[i32]) -> AppResult<std::path::PathBuf> {
    if ids.is_empty() {
        return Err(AppError::Biz("ID 不能为空".into()));
    }
    let bills = query::find_all_for_export(app, ids).await?;
    let bill_ids: Vec<i32> = bills.iter().map(|bill| bill.id).collect();
    let mut attachments = relation::find_attachments(app, &bill_ids).await?;

    let temp_dir = crate::attach::service::scratch_dir(app, "bill-export")?;

    let mut rows: Vec<Vec<String>> = Vec::with_capacity(bills.len() + 2);
    rows.push(
        [
            "单据编号",
            "状态",
            "项目名称",
            "项目地址",
            "总额",
            "安装完成日期",
            "备注",
        ]
        .iter()
        .map(|value| value.to_string())
        .collect(),
    );
    let mut total_amount = 0.0;
    // 明细、主表字段与附件目录一次遍历完成：导出后单据不再复用，字段直接移动
    for bill in bills {
        let mut detail_text = String::new();
        for detail in &bill.details {
            detail_text.push_str(&format!(
                "{} : {:.2} * {:.2} ; ",
                detail.device, detail.unit_price, detail.quantity
            ));
        }
        let processed = bill
            .processed_date
            .map(|date| date.format("%Y-%m-%d").to_string())
            .unwrap_or_default();
        total_amount += bill.total_amount;

        let dir = crate::util::safe_join(&temp_dir, &bill.number)
            .map_err(|_| AppError::Biz("单号不合法".into()))?;
        let attachment_ids: Vec<i32> = attachments
            .remove(&bill.id)
            .unwrap_or_default()
            .into_iter()
            .map(|row| row.id)
            .collect();
        crate::attach::service::copy_to(app, &attachment_ids, &dir).await?;

        rows.push(vec![
            bill.number,
            bill.state.as_str().to_string(),
            bill.project_name,
            bill.project_address,
            format!("{:.2}", bill.total_amount),
            processed,
            detail_text,
        ]);
    }
    rows.push(vec![
        String::new(),
        "合计".into(),
        format!("{total_amount:.2}"),
        String::new(),
        String::new(),
    ]);

    let excel_path = crate::util::safe_join(
        &temp_dir,
        &format!("导出结果{}.xlsx", Utc::now().format("%Y%m%d%H%M%S")),
    )?;
    crate::attach::office::generate_excel(&rows, &excel_path)?;
    let zip_path = crate::attach::service::zip_directory(app, &temp_dir)?;
    let _ = std::fs::remove_dir_all(&temp_dir);
    Ok(zip_path)
}

/// 装载带明细、产品公司、附件与负责人的完整 DTO
async fn load_full(app: &AppState, id: i32) -> AppResult<BillDto> {
    let Some(full) = store::find_full_by_id(&app.db, id).await? else {
        return Err(AppError::Biz("单据不存在".into()));
    };
    let attachments = relation::find_attachment_dtos(app, id).await?;
    let assignees = query::find_assignees(app, id).await?;
    Ok(full_dto(full, attachments, assignees))
}

/// 校验指派对象是启用用户；与自己相同的指派不算越权
async fn resolve_user_ids(app: &AppState, ids: &[i32]) -> AppResult<Vec<i32>> {
    let unique = crate::util::unique_ids(ids);
    let current = query::current_user_id()?;
    for id in &unique {
        if *id == current {
            continue;
        }
        if crate::user::service::find_enabled_by_id(&app.db, *id)
            .await?
            .is_none()
        {
            return Err(AppError::Biz("指派用户不存在或已禁用".into()));
        }
    }
    Ok(unique)
}

/// 写动作只清理列表缓存；金额统计按 TTL 刷新，允许短时间不一致。
async fn evict_query(app: &AppState) {
    app.cache.invalidate(query::QUERY_CACHE).await;
}

/// 批量写的公共骨架：逐项执行，单项失败不影响其他项
async fn for_each_id<F, Fut>(ids: &[i32], action: F) -> ActionsResult<i32>
where
    F: Fn(i32) -> Fut,
    Fut: std::future::Future<Output = AppResult<()>>,
{
    let mut rows = Vec::with_capacity(ids.len());
    for id in ids.iter().copied() {
        rows.push(action_row(id, action(id).await));
    }
    ActionsResult::new(rows)
}

fn action_row(id: i32, result: AppResult<()>) -> ActionRow<i32> {
    match result {
        Ok(()) => ActionRow::success(id),
        Err(error) => {
            tracing::warn!("操作失败 {id}: {error}");
            ActionRow::failure(id, error.to_string())
        }
    }
}
