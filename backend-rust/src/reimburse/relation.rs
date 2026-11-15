use std::collections::{HashMap, HashSet};

use sqlx::FromRow;

use crate::attach::model::AttachDto;
use crate::attach::store::{self, AttachRow};
use crate::db::current_operator_id;
use crate::error::AppResult;
use crate::state::AppState;

#[derive(Debug, Clone, FromRow)]
pub struct RelationRow {
    pub id: i32,
    pub reimburse_id: Option<i32>,
    pub attach_id: Option<i32>,
}

pub async fn find_relations_by_bill_ids(
    db: &crate::db::Db,
    bill_ids: &[i32],
) -> AppResult<Vec<RelationRow>> {
    if bill_ids.is_empty() {
        return Ok(Vec::new());
    }
    Ok(sqlx::query_as::<_, RelationRow>(
        "SELECT id, reimburse_id, attach_id FROM reimburse_attach_relation \
         WHERE reimburse_id = ANY($1) ORDER BY id",
    )
    .bind(bill_ids)
    .fetch_all(db.pool())
    .await?)
}

pub async fn find_attachments(
    app: &AppState,
    bill_ids: &[i32],
) -> AppResult<HashMap<i32, Vec<AttachRow>>> {
    let mut grouped: HashMap<i32, Vec<AttachRow>> = HashMap::new();
    let relations = find_relations_by_bill_ids(&app.db, bill_ids).await?;
    if relations.is_empty() {
        return Ok(grouped);
    }
    let mut attach_ids: Vec<i32> = relations.iter().filter_map(|row| row.attach_id).collect();
    attach_ids.sort_unstable();
    attach_ids.dedup();
    let mut by_id: HashMap<i32, AttachRow> = store::find_by_ids(&app.db, &attach_ids)
        .await?
        .into_iter()
        .map(|row| (row.id, row))
        .collect();
    // 一个附件在结构上只属于一张单据，行数据直接取走即可
    for relation in relations {
        if let (Some(bill_id), Some(attach_id)) = (relation.reimburse_id, relation.attach_id)
            && let Some(row) = by_id.remove(&attach_id)
        {
            grouped.entry(bill_id).or_default().push(row);
        }
    }
    Ok(grouped)
}

pub async fn find_attachment_dtos(app: &AppState, bill_id: i32) -> AppResult<Vec<AttachDto>> {
    let mut map = find_attachments(app, &[bill_id]).await?;
    Ok(map
        .remove(&bill_id)
        .unwrap_or_default()
        .into_iter()
        .map(AttachDto::from)
        .collect())
}

pub async fn update_attachments(
    app: &AppState,
    conn: &mut sqlx::PgConnection,
    bill_id: i32,
    target: Option<&[AttachDto]>,
) -> AppResult<()> {
    let relations: Vec<RelationRow> = sqlx::query_as(
        "SELECT id, reimburse_id, attach_id FROM reimburse_attach_relation \
         WHERE reimburse_id = $1 ORDER BY id",
    )
    .bind(bill_id)
    .fetch_all(&mut *conn)
    .await?;
    let current: HashSet<i32> = relations.iter().filter_map(|row| row.attach_id).collect();

    let mut added: Vec<i32> = Vec::new();
    let mut target_ids: HashSet<i32> = HashSet::new();
    for dto in target.unwrap_or_default() {
        if dto.id == 0 || !target_ids.insert(dto.id) {
            continue;
        }
        if !current.contains(&dto.id) {
            added.push(dto.id);
        }
    }
    let removed: Vec<i32> = relations
        .iter()
        .filter_map(|row| row.attach_id)
        .filter(|id| !target_ids.contains(id))
        .collect();
    if added.is_empty() && removed.is_empty() {
        return Ok(());
    }

    if !added.is_empty() {
        crate::attach::service::persist(conn, &added).await?;
        let operator = current_operator_id();
        sqlx::query(
            "INSERT INTO reimburse_attach_relation (reimburse_id, attach_id) \
             SELECT $1, unnest($2::int[])",
        )
        .bind(bill_id)
        .bind(&added)
        .execute(&mut *conn)
        .await
        .map_err(|error| crate::db::map_db_error(error, "附件已关联"))?;
        let _ = operator;
    }
    if !removed.is_empty() {
        sqlx::query(
            "DELETE FROM reimburse_attach_relation WHERE reimburse_id = $1 AND attach_id = ANY($2)",
        )
        .bind(bill_id)
        .bind(&removed)
        .execute(&mut *conn)
        .await?;
        crate::attach::service::delete(app, conn, &removed).await?;
    }
    Ok(())
}

pub async fn delete_relations(conn: &mut sqlx::PgConnection, bill_id: i32) -> AppResult<()> {
    sqlx::query("DELETE FROM reimburse_attach_relation WHERE reimburse_id = $1")
        .bind(bill_id)
        .execute(&mut *conn)
        .await?;
    Ok(())
}
