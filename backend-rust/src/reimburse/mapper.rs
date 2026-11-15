use crate::attach::content;
use crate::attach::model::AttachDto;
use crate::error::{AppError, AppResult};
use crate::reimburse::model::{ReimburseDetailDto, ReimburseDto, ReimburseState};
use crate::state::AppState;

/// 报销单只支持标准税务发票（文本），映射规则固定，无需多规则注册。
pub async fn map(app: &AppState, attachment: &AttachDto) -> AppResult<ReimburseDto> {
    if attachment.id == 0 {
        return Err(AppError::Biz("附件为空".into()));
    }
    let texts = content::texts(app, attachment).await?;
    from_texts(&texts).ok_or_else(|| AppError::Biz("未配置映射规则".into()))
}

fn can_map(texts: &[String]) -> bool {
    texts
        .iter()
        .any(|text| text.contains("发票") || text.contains("价税合计"))
}

/// 税务发票文本 → 报销单。
///
/// 明细区从「项目名称」表头行开始，到「合计」行结束；每行按空白切分，
/// 名称为首项，金额 = 倒数第三项（金额） + 末项（税额）。
fn from_texts(texts: &[String]) -> Option<ReimburseDto> {
    if !can_map(texts) {
        return None;
    }
    let mut dto = ReimburseDto {
        id: 0,
        number: String::new(),
        state: ReimburseState::Created,
        summary: "发票报销".into(),
        total_amount: 0.0,
        reimburse_date: None,
        remark: String::new(),
        details: Vec::new(),
        attachments: Vec::new(),
    };
    let mut index = 0;

    // 表头之前出现的「开票日期」决定报销日期
    let mut header_index: Option<usize> = None;
    for (position, text) in texts.iter().enumerate() {
        if text.contains("开票日期") {
            let value: String = text.chars().skip(5).collect();
            if let Some(date) = crate::util::parse_datetime(&value) {
                dto.reimburse_date = Some(date);
            }
        }
        if text.contains("项目名称") {
            header_index = Some(position);
            index = position;
            break;
        }
    }

    if let Some(header) = header_index {
        for (position, text) in texts.iter().enumerate().skip(header + 1) {
            let line = text.trim();
            if line.contains('合') && line.contains('计') {
                index = position;
                break;
            }
            let tokens: Vec<&str> = line.split_whitespace().collect();
            // 纯符号行没有足够的分项
            if tokens.len() < 4 {
                continue;
            }
            let mut amount = 0.0;
            if tokens.len() >= 3 {
                amount += tokens[tokens.len() - 3].parse::<f64>().unwrap_or(0.0);
                amount += tokens[tokens.len() - 1].parse::<f64>().unwrap_or(0.0);
            }
            let name = tokens[0];
            if !name.is_empty() {
                dto.details.push(ReimburseDetailDto {
                    id: 0,
                    name: name.to_string(),
                    amount,
                });
            }
        }
    }

    // 价税合计：取「小写）」之后的金额
    for (position, text) in texts.iter().enumerate().skip(index) {
        if text.contains("价税合计") {
            if let Some(after) = text.split("小写）").nth(1) {
                let value = after.trim().trim_start_matches(['¥', '￥']);
                dto.total_amount = value.parse().unwrap_or(0.0);
            }
            index = position;
            break;
        }
    }

    // 备注：「备」「注」两行之后的文本
    let mut remark_start: Option<usize> = None;
    for position in index..texts.len() {
        if texts[position].trim() == "备"
            && position + 1 < texts.len()
            && texts[position + 1] == "注"
        {
            remark_start = Some(position + 2);
            break;
        }
    }
    if let Some(start) = remark_start {
        dto.remark = texts[start..]
            .iter()
            .map(|text| text.trim())
            .filter(|text| !text.is_empty())
            .collect::<Vec<_>>()
            .join("\n");
    }

    Some(dto)
}
