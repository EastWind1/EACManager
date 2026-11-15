use chrono::Utc;

use crate::error::{AppError, AppResult};
use crate::model::{ActionRow, ActionsResult};
use crate::reimburse::model::{
    Reimburse, ReimburseDto, ReimburseRequest, ReimburseState, full_dto,
};
use crate::reimburse::{mapper, query, relation, store};
use crate::state::AppState;

pub const ATTACH_NAMESPACE: &str = "REIMBURSEMENT";

pub fn generate_number() -> String {
    format!("R{}", crate::util::gen_int_id())
}

pub async fn create(app: &AppState, request: ReimburseRequest) -> AppResult<ReimburseDto> {
    request.validate()?;
    if request.id != 0 {
        return Err(AppError::Biz("单据 ID 不为空".into()));
    }
    let (mut entity, attachments) = request.into_parts();
    if entity.number.is_empty() {
        entity.number = generate_number();
    } else {
        crate::util::valid_number(&entity.number)?;
    }
    entity.state = ReimburseState::Created;

    let id = app
        .db
        .transaction(async |conn| {
            let id = store::insert(conn, &entity).await?;
            relation::update_attachments(app, conn, id, Some(&attachments)).await?;
            Ok(id)
        })
        .await?;

    evict_statistic(app);
    load_full(app, id).await
}

pub async fn update(app: &AppState, request: ReimburseRequest) -> AppResult<ReimburseDto> {
    request.validate()?;
    if request.id == 0 {
        return Err(AppError::Biz("id不能为空".into()));
    }
    let (entity, attachments) = request.into_parts();

    app.db
        .transaction(async |conn| {
            let existing = query::require_by_id(app, entity.id).await?;
            if entity.number != existing.number {
                crate::util::valid_number(&entity.number)?;
            }
            store::update(conn, &entity).await?;
            relation::update_attachments(app, conn, entity.id, Some(&attachments)).await?;
            Ok(())
        })
        .await?;

    evict_statistic(app);
    load_full(app, entity.id).await
}

pub async fn delete(app: &AppState, ids: &[i32]) -> AppResult<ActionsResult<i32>> {
    if ids.is_empty() {
        return Err(AppError::Biz("ID 为空".into()));
    }
    let rows = for_each_id(ids, |id| async move {
        app.db
            .transaction(async |conn| {
                let entity = query::require_by_id(app, id).await?;
                if entity.state != ReimburseState::Created {
                    return Err(AppError::Biz("非创建状态不能删除".into()));
                }
                relation::update_attachments(app, conn, entity.id, None).await?;
                if !store::delete(conn, entity.id).await? {
                    return Err(AppError::Biz("数据已被更改，稍后重试".into()));
                }
                Ok(())
            })
            .await
    })
    .await;
    evict_statistic(app);
    Ok(rows)
}

async fn transition<F>(app: &AppState, ids: &[i32], apply: F) -> AppResult<ActionsResult<i32>>
where
    F: Fn(&mut Reimburse) -> AppResult<()> + Copy,
{
    if ids.is_empty() {
        return Err(AppError::Biz("ID 为空".into()));
    }
    let rows = for_each_id(ids, |id| async move {
        app.db
            .transaction(async |conn| {
                let mut entity = query::require_by_id(app, id).await?;
                apply(&mut entity)?;
                store::update_state(conn, &entity).await
            })
            .await
    })
    .await;
    evict_statistic(app);
    Ok(rows)
}

pub async fn process(app: &AppState, ids: &[i32]) -> AppResult<ActionsResult<i32>> {
    transition(app, ids, |entity| {
        entity.transition_from(
            ReimburseState::Created,
            ReimburseState::Processing,
            "非创建状态的单据不能处理",
        )
    })
    .await
}

pub async fn finish(app: &AppState, ids: &[i32]) -> AppResult<ActionsResult<i32>> {
    transition(app, ids, |entity| {
        entity.transition_from(
            ReimburseState::Processing,
            ReimburseState::Finished,
            "非处理中状态的单据不能完成",
        )
    })
    .await
}

pub async fn cancel_process(app: &AppState, ids: &[i32]) -> AppResult<ActionsResult<i32>> {
    transition(app, ids, |entity| {
        entity.transition_from(
            ReimburseState::Processing,
            ReimburseState::Created,
            "非处理中状态的单据不能取消处理",
        )
    })
    .await
}

pub async fn cancel_finish(app: &AppState, ids: &[i32]) -> AppResult<ActionsResult<i32>> {
    transition(app, ids, |entity| {
        entity.transition_from(
            ReimburseState::Finished,
            ReimburseState::Processing,
            "非完成状态的单据不能取消完成",
        )
    })
    .await
}

/// 上传并解析文件生成单据 DTO，不落库
pub async fn generate_by_file(
    app: &AppState,
    name: String,
    data: Vec<u8>,
) -> AppResult<ReimburseDto> {
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
    let mut dto = mapper::map(app, &attachment).await?;
    dto.attachments = attachments;
    Ok(dto)
}

pub async fn export(app: &AppState, ids: &[i32]) -> AppResult<std::path::PathBuf> {
    use crate::attach::files;
    if ids.is_empty() {
        return Err(AppError::Biz("ID 不能为空".into()));
    }
    let entities = query::find_all_for_export(app, ids).await?;
    let entity_ids: Vec<i32> = entities.iter().map(|entity| entity.id).collect();
    let mut attachments = relation::find_attachments(app, &entity_ids).await?;

    let temp_dir = crate::attach::service::scratch_dir(app, "reimburse-export")?;

    let mut rows: Vec<Vec<String>> = Vec::with_capacity(entities.len() + 2);
    rows.push(
        ["单据编号", "摘要", "总额", "报销日期", "备注"]
            .iter()
            .map(|value| value.to_string())
            .collect(),
    );
    let mut total_amount = 0.0;
    // 主表字段与附件目录一次遍历完成：导出后单据不再复用，字段直接移动
    for entity in entities {
        let reimburse_date = entity
            .reimburse_date
            .map(|date| date.format("%Y-%m-%d").to_string())
            .unwrap_or_default();
        total_amount += entity.total_amount;

        let dir = crate::util::safe_join(&temp_dir, &entity.number)
            .map_err(|_| AppError::Biz("单号不合法".into()))?;
        std::fs::create_dir_all(&dir)?;
        for row in attachments.remove(&entity.id).unwrap_or_default() {
            let origin = crate::attach::service::absolute_path_by_relative(
                app,
                row.relative_path.as_deref().unwrap_or_default(),
            )
            .await?;
            let target = files::unique_target_path(&dir, row.name.as_deref().unwrap_or_default())
                .map_err(|_| AppError::Biz("附件名不合法".into()))?;
            files::copy_file(&origin, &target)?;
        }

        rows.push(vec![
            entity.number,
            entity.summary,
            format!("{:.2}", entity.total_amount),
            reimburse_date,
            entity.remark,
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
    let zip_path = files::zip_dir(&temp_dir, Some(&temp_dir.with_extension("zip")))?;
    let _ = std::fs::remove_dir_all(&temp_dir);
    Ok(zip_path)
}

async fn load_full(app: &AppState, id: i32) -> AppResult<ReimburseDto> {
    let Some(full) = store::find_full_by_id(&app.db, id).await? else {
        return Err(AppError::Biz("单据不存在".into()));
    };
    let attachments = relation::find_attachment_dtos(app, id).await?;
    Ok(full_dto(full, attachments))
}

fn evict_statistic(app: &AppState) {
    app.cache.invalidate(STAT_NAMESPACE);
}

const STAT_NAMESPACE: &str = "reimburse";

/// 批量写的公共骨架：逐项执行，单项失败不影响其他项
async fn for_each_id<F, Fut>(ids: &[i32], action: F) -> ActionsResult<i32>
where
    F: Fn(i32) -> Fut,
    Fut: std::future::Future<Output = AppResult<()>>,
{
    let mut rows = Vec::with_capacity(ids.len());
    for id in ids.iter().copied() {
        let row = match action(id).await {
            Ok(()) => ActionRow::success(id),
            Err(error) => {
                tracing::warn!("操作失败 {id}: {error}");
                ActionRow::failure(id, error.to_string())
            }
        };
        rows.push(row);
    }
    ActionsResult::new(rows)
}
