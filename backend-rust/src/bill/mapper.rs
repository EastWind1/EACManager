use chrono::Utc;

use crate::attach::content;
use crate::attach::model::{AttachDto, AttachType};
use crate::bill::model::{BillDetailDto, BillDto, BillState, BillType};
use crate::error::{AppError, AppResult};
use crate::state::AppState;

/// 威垦/菱电共用一套表单模板，只有识别关键词与公司名不同。
#[derive(Debug, Clone, Copy)]
struct MapRule {
    keyword: &'static str,
}

const RULES: [MapRule; 2] = [MapRule { keyword: "菱电" }, MapRule { keyword: "威垦" }];

/// 解析附件内容为服务单 DTO；只做映射，不落库。
///
/// 规则按顺序尝试，命中即返回：菱电判定更具体，排在威垦之前。
pub async fn map(app: &AppState, attachment: &AttachDto) -> AppResult<BillDto> {
    if attachment.id == 0 {
        return Err(AppError::Biz("附件为空".into()));
    }
    let rows = match attachment.attach_type {
        AttachType::Excel => content::grid(app, attachment).await?,
        _ => {
            let texts = content::texts(app, attachment).await?;
            return from_texts(app, &texts).await;
        }
    };
    from_grid(app, &rows).await
}

async fn from_texts(app: &AppState, texts: &[String]) -> AppResult<BillDto> {
    for rule in RULES {
        if !texts.iter().any(|text| text.contains(rule.keyword)) {
            continue;
        }
        let mut dto = empty_dto();
        set_company(app, &mut dto, rule.keyword).await?;
        for text in texts {
            set_by_text(&mut dto, text);
        }
        return Ok(dto);
    }
    Err(AppError::Biz("未配置映射规则".into()))
}

async fn from_grid(app: &AppState, rows: &[Vec<String>]) -> AppResult<BillDto> {
    for rule in RULES {
        if !rows
            .iter()
            .any(|row| row.iter().any(|text| text.contains(rule.keyword)))
        {
            continue;
        }
        return map_grid(app, rows, rule).await;
    }
    Err(AppError::Biz("未配置映射规则".into()))
}

fn empty_dto() -> BillDto {
    BillDto {
        id: 0,
        number: String::new(),
        bill_type: BillType::Install,
        state: BillState::Created,
        product_company: None,
        assignees: Vec::new(),
        project_name: String::new(),
        project_address: String::new(),
        project_contact: String::new(),
        project_contact_phone: String::new(),
        on_site_contact: String::new(),
        on_site_phone: String::new(),
        elevator_info: String::new(),
        total_amount: 0.0,
        order_date: None,
        processed_date: None,
        finished_date: None,
        remark: String::new(),
        details: Vec::new(),
        attachments: Vec::new(),
    }
}

async fn set_company(app: &AppState, dto: &mut BillDto, keyword: &str) -> AppResult<()> {
    let companies = crate::company::service::find_by_name(app, keyword).await?;
    dto.product_company = companies.first().cloned();
    Ok(())
}

/// 文本行形如「标签：值」，标签决定写入哪个字段
fn set_by_text(dto: &mut BillDto, text: &str) {
    if text.is_empty() {
        return;
    }
    let Some((label, value)) = split_label(text) else {
        return;
    };
    match label.trim() {
        "合同编号" => dto.number = value.trim().to_string(),
        "下单时间" => {
            dto.order_date = crate::util::parse_datetime(value);
        }
        "项目名称" => dto.project_name = value.trim().to_string(),
        "监理、站长" => dto.project_contact = value.trim().to_string(),
        "现场联系人" => dto.on_site_contact = value.trim().to_string(),
        "项目地址" => dto.project_address = value.trim().to_string(),
        "备注" => dto.remark = value.trim().to_string(),
        _ => {}
    }
}

fn split_label(text: &str) -> Option<(&str, &str)> {
    if let Some((label, value)) = text.split_once('：') {
        return Some((label, value));
    }
    let (label, value) = text.split_once(':')?;
    Some((label, value))
}

async fn map_grid(app: &AppState, rows: &[Vec<String>], rule: MapRule) -> AppResult<BillDto> {
    let mut dto = empty_dto();
    set_company(app, &mut dto, rule.keyword).await?;

    // 明细区由「序号」表头开启，遇到「出货信息」结束
    let mut in_details = false;
    for row in rows {
        if row.first().is_some_and(|text| text.contains("序号")) {
            in_details = true;
            continue;
        }
        if row.first().is_some_and(|text| text.contains("出货信息")) {
            in_details = false;
        }
        if !in_details {
            for text in row {
                set_by_text(&mut dto, text);
            }
            continue;
        }
        if row.first().is_some_and(|text| is_number(text)) {
            if let Some(detail) = detail_from_row(row) {
                dto.details.push(detail);
            }
            continue;
        }
        for (index, text) in row.iter().enumerate() {
            if text.contains('路') && index + 2 < row.len() && !row[index + 2].is_empty() {
                let price = parse_f64(&row[index + 2]);
                dto.details.push(BillDetailDto {
                    id: 0,
                    device: "路费补贴".into(),
                    quantity: 1.0,
                    unit_price: price,
                    subtotal: price,
                    remark: String::new(),
                });
            }
            if text.contains("合计") && index + 2 < row.len() {
                dto.total_amount = parse_f64(&row[index + 2]);
            }
        }
    }
    if dto.order_date.is_none() {
        dto.order_date = Some(Utc::now());
    }
    Ok(dto)
}

fn detail_from_row(row: &[String]) -> Option<BillDetailDto> {
    // 没有数量的行不是明细
    if row.get(5).is_none_or(|value| value.trim().is_empty()) {
        return None;
    }
    let mut device = row.get(1).cloned().unwrap_or_default();
    for index in [2, 4] {
        if let Some(part) = row.get(index)
            && !part.trim().is_empty()
        {
            device.push(' ');
            device.push_str(part);
        }
    }
    let quantity = parse_f64(row.get(5).map(String::as_str).unwrap_or(""));
    let unit_price = parse_f64(row.get(7).map(String::as_str).unwrap_or(""));
    let subtotal = parse_f64(row.get(8).map(String::as_str).unwrap_or(""));
    Some(BillDetailDto {
        id: 0,
        device: device.trim().to_string(),
        quantity,
        unit_price,
        subtotal,
        remark: String::new(),
    })
}

fn is_number(value: &str) -> bool {
    let trimmed = value.trim();
    !trimmed.is_empty() && trimmed.chars().all(|ch| ch.is_ascii_digit() || ch == '.')
}

fn parse_f64(value: &str) -> f64 {
    value.trim().parse().unwrap_or(0.0)
}
