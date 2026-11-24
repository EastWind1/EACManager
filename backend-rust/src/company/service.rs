use crate::company::model::{CompanyDto, CompanyRequest};
use crate::company::store::{self, CompanyRow};
use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::model::{PageResult, QueryParam};

pub async fn find_enabled(db: &Db, param: &QueryParam) -> AppResult<PageResult<CompanyDto>> {
    param.validate().map_err(AppError::Biz)?;
    let order = param.order_by().map_err(AppError::Biz)?;
    let order = match (order.as_str(), param.has_page()) {
        ("", true) => "id DESC".to_string(),
        _ => order,
    };
    let total = store::count_enabled(db).await?;
    if total == 0 {
        return Ok(PageResult::empty(param));
    }
    let rows = store::find_enabled_page(db, &order, param.page_size(), param.offset()).await?;
    let items = rows.into_iter().map(CompanyDto::from).collect();
    Ok(PageResult::new(items, total, param))
}

pub async fn find_by_name(db: &Db, name: &str) -> AppResult<Vec<CompanyDto>> {
    Ok(store::find_enabled_by_name_contains(db, name)
        .await?
        .into_iter()
        .map(CompanyDto::from)
        .collect())
}

pub async fn create(db: &Db, request: &CompanyRequest) -> AppResult<CompanyDto> {
    request.validate()?;
    let row = store::create(
        db,
        request.name.trim(),
        request.contact_name(),
        request.contact_phone(),
        request.email(),
        request.address(),
    )
    .await?;
    Ok(CompanyDto::from(row))
}

pub async fn update(db: &Db, request: &CompanyRequest) -> AppResult<CompanyDto> {
    if request.id == 0 {
        return Err(AppError::Biz("公司 ID 不能为空".into()));
    }
    request.validate()?;
    let row = store::update(
        db,
        request.id,
        request.name.trim(),
        request.contact_name(),
        request.contact_phone(),
        request.email(),
        request.address(),
    )
    .await?;
    Ok(CompanyDto::from(row))
}

/// 返回任意状态的公司（含已停用），供单据关联校验。
pub async fn find_by_id(db: &Db, id: i32) -> AppResult<Option<CompanyDto>> {
    if id == 0 {
        return Ok(None);
    }
    Ok(store::find_by_id(db, id).await?.map(CompanyDto::from))
}

pub async fn disable(db: &Db, id: i32) -> AppResult<()> {
    if id == 0 {
        return Err(AppError::Biz("公司 ID 不能为空".into()));
    }
    store::set_disabled(db, id).await
}

/// 供服务单 DTO 补全产品公司
pub async fn find_by_ids(db: &Db, ids: &[i32]) -> AppResult<Vec<CompanyRow>> {
    store::find_by_ids(db, ids).await
}
