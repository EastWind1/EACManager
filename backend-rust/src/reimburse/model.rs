use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

use crate::attach::model::AttachDto;
use crate::error::{AppError, AppResult};
use crate::model::QueryParam;

/// 报销单状态：CREATED(0) → PROCESSING(1) → FINISHED(2)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum ReimburseState {
    Created,
    Processing,
    Finished,
}

impl ReimburseState {
    pub fn code(&self) -> i16 {
        match self {
            Self::Created => 0,
            Self::Processing => 1,
            Self::Finished => 2,
        }
    }

    pub fn from_code(code: i16) -> Self {
        match code {
            1 => Self::Processing,
            2 => Self::Finished,
            _ => Self::Created,
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Created => "CREATED",
            Self::Processing => "PROCESSING",
            Self::Finished => "FINISHED",
        }
    }
}

#[derive(Debug, Clone)]
pub struct Reimburse {
    pub id: i32,
    pub number: String,
    pub state: ReimburseState,
    pub summary: String,
    pub total_amount: f64,
    pub reimburse_date: Option<DateTime<Utc>>,
    pub remark: String,
    pub details: Vec<ReimburseDetail>,
}

#[derive(Debug, Clone)]
pub struct ReimburseDetail {
    pub id: i32,
    pub reimburse_id: i32,
    pub name: String,
    pub amount: f64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReimburseDto {
    pub id: i32,
    pub number: String,
    pub state: ReimburseState,
    pub summary: String,
    pub total_amount: f64,
    pub reimburse_date: Option<DateTime<Utc>>,
    pub remark: String,
    pub details: Vec<ReimburseDetailDto>,
    pub attachments: Vec<AttachDto>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReimburseDetailDto {
    pub id: i32,
    pub name: String,
    pub amount: f64,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReimburseRequest {
    #[serde(default)]
    pub id: i32,
    #[serde(default)]
    pub number: String,
    #[serde(default)]
    pub state: Option<ReimburseState>,
    #[serde(default)]
    pub summary: String,
    #[serde(default)]
    pub total_amount: f64,
    #[serde(default)]
    pub reimburse_date: Option<DateTime<Utc>>,
    #[serde(default)]
    pub remark: Option<String>,
    #[serde(default)]
    pub details: Option<Vec<ReimburseDetailRequest>>,
    #[serde(default)]
    pub attachments: Option<Vec<AttachDto>>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReimburseDetailRequest {
    #[serde(default)]
    pub id: i32,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub amount: f64,
}

#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct ReimburseQueryParam {
    pub number: Option<String>,
    pub summary: Option<String>,
    pub states: Option<Vec<ReimburseState>>,
    pub reimburse_start_date: Option<String>,
    pub reimburse_end_date: Option<String>,
    #[serde(flatten)]
    pub page: QueryParam,
}

/// 去除首尾空白；没有多余空白时原样返回，省掉一次分配
fn trimmed(value: String) -> String {
    if value.trim().len() == value.len() {
        value
    } else {
        value.trim().to_string()
    }
}

impl ReimburseRequest {
    pub fn validate(&self) -> crate::error::AppResult<()> {
        if self.summary.trim().is_empty() {
            return Err(crate::error::AppError::Biz("摘要不能为空".into()));
        }
        if self.summary.chars().count() > 255 {
            return Err(crate::error::AppError::Biz(
                "摘要不能超过 255 个字符".into(),
            ));
        }
        for detail in self.details.as_deref().unwrap_or_default() {
            if detail.name.trim().is_empty() {
                return Err(crate::error::AppError::Biz("项目名称不能为空".into()));
            }
            if detail.name.chars().count() > 255 {
                return Err(crate::error::AppError::Biz(
                    "项目名称不能超过 255 个字符".into(),
                ));
            }
        }
        Ok(())
    }

    /// 请求只用于构造一次实体：字段直接移动，避免把整份单据复制一遍
    pub fn into_parts(self) -> (Reimburse, Vec<AttachDto>) {
        let attachments = self.attachments.unwrap_or_default();
        let id = self.id;
        let entity = Reimburse {
            id,
            number: trimmed(self.number),
            state: self.state.unwrap_or(ReimburseState::Created),
            summary: trimmed(self.summary),
            total_amount: self.total_amount,
            reimburse_date: self.reimburse_date,
            remark: self.remark.unwrap_or_default(),
            details: self
                .details
                .unwrap_or_default()
                .into_iter()
                .map(|detail| ReimburseDetail {
                    id: detail.id,
                    reimburse_id: id,
                    name: trimmed(detail.name),
                    amount: detail.amount,
                })
                .collect(),
        };
        (entity, attachments)
    }
}

/// 实体转 DTO 一律移动字段：查询路径拿到实体后不再复用，克隆只会白白复制字符串。
pub fn base_dto(entity: Reimburse) -> ReimburseDto {
    ReimburseDto {
        id: entity.id,
        number: entity.number,
        state: entity.state,
        summary: entity.summary,
        total_amount: entity.total_amount,
        reimburse_date: entity.reimburse_date,
        remark: entity.remark,
        details: Vec::new(),
        attachments: Vec::new(),
    }
}

pub fn full_dto(mut entity: Reimburse, attachments: Vec<AttachDto>) -> ReimburseDto {
    let details = std::mem::take(&mut entity.details);
    let mut dto = base_dto(entity);
    dto.details = details
        .into_iter()
        .map(|detail| ReimburseDetailDto {
            id: detail.id,
            name: detail.name,
            amount: detail.amount,
        })
        .collect();
    dto.attachments = attachments;
    dto
}

/// 报销单模块的权限清单
pub fn permissions() -> Vec<crate::security::model::PermissionDto> {
    use crate::security::model::PermissionDto;
    vec![
        PermissionDto::definition("reimburse:query", "reimburse", "报销单", "查看报销单", 1),
        PermissionDto::definition(
            "reimburse:detail",
            "reimburse",
            "报销单",
            "查看报销单详情",
            2,
        ),
        PermissionDto::definition("reimburse:create", "reimburse", "报销单", "新建报销单", 3),
        PermissionDto::definition("reimburse:update", "reimburse", "报销单", "修改报销单", 4),
        PermissionDto::definition("reimburse:delete", "reimburse", "报销单", "删除报销单", 5),
        PermissionDto::definition("reimburse:import", "reimburse", "报销单", "导入报销单", 6),
        PermissionDto::definition("reimburse:export", "reimburse", "报销单", "导出报销单", 7),
        PermissionDto::definition("reimburse:process", "reimburse", "报销单", "处理报销单", 8),
        PermissionDto::definition("reimburse:finish", "reimburse", "报销单", "完成报销单", 9),
        PermissionDto::definition(
            "reimburse:cancel-process",
            "reimburse",
            "报销单",
            "取消处理报销单",
            10,
        ),
        PermissionDto::definition(
            "reimburse:cancel-finish",
            "reimburse",
            "报销单",
            "取消完成报销单",
            11,
        ),
        PermissionDto::definition(
            "reimburse:statistic",
            "reimburse",
            "报销单",
            "统计报销单",
            12,
        ),
    ]
}
impl Reimburse {
    /// 状态机：只接受精确的前置状态，不匹配则整体拒绝本次流转
    pub fn transition_from(
        &mut self,
        from: ReimburseState,
        to: ReimburseState,
        message: &str,
    ) -> AppResult<()> {
        if self.state != from {
            return Err(AppError::Biz(message.into()));
        }
        self.state = to;
        Ok(())
    }
}
