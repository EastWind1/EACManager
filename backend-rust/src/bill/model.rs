use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

use crate::attach::model::AttachDto;
use crate::company::model::CompanyDto;
use crate::model::QueryParam;

/// 服务单状态：CREATED(0) → PROCESSING(1) → PROCESSED(2) → FINISHED(3)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum BillState {
    Created,
    Processing,
    Processed,
    Finished,
}

impl BillState {
    pub fn code(&self) -> i16 {
        match self {
            Self::Created => 0,
            Self::Processing => 1,
            Self::Processed => 2,
            Self::Finished => 3,
        }
    }

    pub fn from_code(code: i16) -> Self {
        match code {
            1 => Self::Processing,
            2 => Self::Processed,
            3 => Self::Finished,
            _ => Self::Created,
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Created => "CREATED",
            Self::Processing => "PROCESSING",
            Self::Processed => "PROCESSED",
            Self::Finished => "FINISHED",
        }
    }

    /// 只有新建、处理中的单据允许指派或取消指派
    pub fn is_assignable(&self) -> bool {
        matches!(self, Self::Created | Self::Processing)
    }
}

/// 服务单类型：INSTALL(0) / FIX(1)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum BillType {
    Install,
    Fix,
}

impl BillType {
    pub fn code(&self) -> i16 {
        match self {
            Self::Install => 0,
            Self::Fix => 1,
        }
    }

    pub fn from_code(code: i16) -> Self {
        match code {
            1 => Self::Fix,
            _ => Self::Install,
        }
    }
}

#[derive(Debug, Clone)]
pub struct Bill {
    pub id: i32,
    pub number: String,
    pub bill_type: BillType,
    pub state: BillState,
    pub product_company_id: Option<i32>,
    pub project_name: String,
    pub project_address: String,
    pub project_contact: String,
    pub project_contact_phone: String,
    pub on_site_contact: String,
    pub on_site_phone: String,
    pub elevator_info: String,
    pub total_amount: f64,
    pub order_date: Option<DateTime<Utc>>,
    pub processed_date: Option<DateTime<Utc>>,
    pub finished_date: Option<DateTime<Utc>>,
    pub remark: String,
    pub details: Vec<BillDetail>,
    pub product_company: Option<CompanyDto>,
}

#[derive(Debug, Clone)]
pub struct BillDetail {
    pub id: i32,
    pub bill_id: i32,
    pub device: String,
    pub quantity: f64,
    pub unit_price: f64,
    pub subtotal: f64,
    pub remark: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BillDto {
    pub id: i32,
    pub number: String,
    #[serde(rename = "type")]
    pub bill_type: BillType,
    pub state: BillState,
    pub product_company: Option<CompanyDto>,
    pub assignees: Vec<AssigneeDto>,
    pub project_name: String,
    pub project_address: String,
    pub project_contact: String,
    #[serde(rename = "projectPhone")]
    pub project_contact_phone: String,
    pub on_site_contact: String,
    pub on_site_phone: String,
    pub elevator_info: String,
    pub total_amount: f64,
    pub order_date: Option<DateTime<Utc>>,
    pub processed_date: Option<DateTime<Utc>>,
    pub finished_date: Option<DateTime<Utc>>,
    pub remark: String,
    pub details: Vec<BillDetailDto>,
    pub attachments: Vec<AttachDto>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AssigneeDto {
    pub id: i32,
    pub username: String,
    pub name: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BillDetailDto {
    pub id: i32,
    pub device: String,
    pub quantity: f64,
    pub unit_price: f64,
    pub subtotal: f64,
    pub remark: String,
}

/// 新建/保存请求：前端把 detail 的 id 回传，需要按 id 对账
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BillRequest {
    #[serde(default)]
    pub id: i32,
    #[serde(default)]
    pub number: String,
    #[serde(rename = "type", default = "default_type")]
    pub bill_type: BillType,
    #[serde(default)]
    pub state: Option<BillState>,
    #[serde(default)]
    pub product_company: Option<CompanyDto>,
    #[serde(default)]
    pub assignees: Option<Vec<AssigneeRef>>,
    #[serde(default)]
    pub project_name: String,
    #[serde(default)]
    pub project_address: String,
    #[serde(default)]
    pub project_contact: Option<String>,
    #[serde(default)]
    pub project_phone: Option<String>,
    #[serde(default)]
    pub on_site_contact: Option<String>,
    #[serde(default)]
    pub on_site_phone: Option<String>,
    #[serde(default)]
    pub elevator_info: Option<String>,
    #[serde(default)]
    pub total_amount: f64,
    #[serde(default)]
    pub order_date: Option<DateTime<Utc>>,
    #[serde(default)]
    pub processed_date: Option<DateTime<Utc>>,
    #[serde(default)]
    pub finished_date: Option<DateTime<Utc>>,
    #[serde(default)]
    pub remark: Option<String>,
    #[serde(default)]
    pub details: Option<Vec<BillDetailRequest>>,
    #[serde(default)]
    pub attachments: Option<Vec<AttachDto>>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AssigneeRef {
    #[serde(default)]
    pub id: i32,
    #[serde(default)]
    pub username: Option<String>,
    #[serde(default)]
    pub name: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BillDetailRequest {
    #[serde(default)]
    pub id: i32,
    #[serde(default)]
    pub device: String,
    #[serde(default)]
    pub quantity: f64,
    #[serde(default)]
    pub unit_price: f64,
    #[serde(default)]
    pub subtotal: f64,
    #[serde(default)]
    pub remark: Option<String>,
}

fn default_type() -> BillType {
    BillType::Install
}

#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct BillQueryParam {
    pub number: Option<String>,
    pub states: Option<Vec<BillState>>,
    pub project_name: Option<String>,
    pub assignee_id: Option<i32>,
    pub order_start_date: Option<String>,
    pub order_end_date: Option<String>,
    pub processed_start_date: Option<String>,
    pub processed_end_date: Option<String>,
    #[serde(flatten)]
    pub page: QueryParam,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AssignParam {
    #[serde(default)]
    pub ids: Vec<i32>,
    #[serde(default)]
    pub assignee_ids: Vec<i32>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProcessedParam {
    #[serde(default)]
    pub ids: Vec<i32>,
    #[serde(default)]
    pub processed_date: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FinishParam {
    #[serde(default)]
    pub ids: Vec<i32>,
    #[serde(default)]
    pub finished_date: Option<DateTime<Utc>>,
}

impl Bill {
    /// 状态机：只接受精确的前置状态，不匹配则整体拒绝本次流转
    pub fn transition_from(
        &mut self,
        from: BillState,
        to: BillState,
        message: &str,
    ) -> Result<(), crate::error::AppError> {
        if self.state != from {
            return Err(crate::error::AppError::Biz(message.into()));
        }
        self.state = to;
        Ok(())
    }
}

/// 去除首尾空白；没有多余空白时原样返回，省掉一次分配
fn trimmed(value: String) -> String {
    if value.trim().len() == value.len() {
        value
    } else {
        value.trim().to_string()
    }
}

impl BillRequest {
    pub fn validate(&self) -> crate::error::AppResult<()> {
        if self.project_name.trim().is_empty() {
            return Err(crate::error::AppError::Biz("项目名称不能为空".into()));
        }
        if self.project_name.chars().count() > 128 {
            return Err(crate::error::AppError::Biz(
                "项目名称不能超过 128 个字符".into(),
            ));
        }
        if self.project_address.trim().is_empty() {
            return Err(crate::error::AppError::Biz("项目地址不能为空".into()));
        }
        if self.project_address.chars().count() > 255 {
            return Err(crate::error::AppError::Biz(
                "项目地址不能超过 255 个字符".into(),
            ));
        }
        for detail in self.details.as_deref().unwrap_or_default() {
            if detail.device.trim().is_empty() {
                return Err(crate::error::AppError::Biz("设备类型不能为空".into()));
            }
            if detail.device.chars().count() > 100 {
                return Err(crate::error::AppError::Biz(
                    "设备类型不能超过 100 个字符".into(),
                ));
            }
        }
        Ok(())
    }

    /// 请求只用于构造一次实体：字段直接移动，避免把整份单据复制一遍
    pub fn into_parts(self) -> (Bill, Vec<AttachDto>) {
        let attachments = self.attachments.unwrap_or_default();
        let id = self.id;
        let bill = Bill {
            id,
            number: trimmed(self.number),
            bill_type: self.bill_type,
            state: self.state.unwrap_or(BillState::Created),
            product_company_id: self
                .product_company
                .as_ref()
                .map(|company| company.id)
                .filter(|value| *value != 0),
            project_name: trimmed(self.project_name),
            project_address: trimmed(self.project_address),
            project_contact: self.project_contact.unwrap_or_default(),
            project_contact_phone: self.project_phone.unwrap_or_default(),
            on_site_contact: self.on_site_contact.unwrap_or_default(),
            on_site_phone: self.on_site_phone.unwrap_or_default(),
            elevator_info: self.elevator_info.unwrap_or_default(),
            total_amount: self.total_amount,
            order_date: self.order_date,
            processed_date: self.processed_date,
            finished_date: self.finished_date,
            remark: self.remark.unwrap_or_default(),
            details: self
                .details
                .unwrap_or_default()
                .into_iter()
                .map(|detail| BillDetail {
                    id: detail.id,
                    bill_id: id,
                    device: trimmed(detail.device),
                    quantity: detail.quantity,
                    unit_price: detail.unit_price,
                    subtotal: detail.subtotal,
                    remark: detail.remark.unwrap_or_default(),
                })
                .collect(),
            product_company: None,
        };
        (bill, attachments)
    }
}

/// 实体转 DTO 一律移动字段：查询路径拿到实体后不再复用，克隆只会白白复制字符串。
pub fn detail_dto(detail: BillDetail) -> BillDetailDto {
    BillDetailDto {
        id: detail.id,
        device: detail.device,
        quantity: detail.quantity,
        unit_price: detail.unit_price,
        subtotal: detail.subtotal,
        remark: detail.remark,
    }
}

pub fn base_dto(bill: Bill) -> BillDto {
    BillDto {
        id: bill.id,
        number: bill.number,
        bill_type: bill.bill_type,
        state: bill.state,
        product_company: bill.product_company,
        assignees: Vec::new(),
        project_name: bill.project_name,
        project_address: bill.project_address,
        project_contact: bill.project_contact,
        project_contact_phone: bill.project_contact_phone,
        on_site_contact: bill.on_site_contact,
        on_site_phone: bill.on_site_phone,
        elevator_info: bill.elevator_info,
        total_amount: bill.total_amount,
        order_date: bill.order_date,
        processed_date: bill.processed_date,
        finished_date: bill.finished_date,
        remark: bill.remark,
        details: Vec::new(),
        attachments: Vec::new(),
    }
}

pub fn full_dto(
    mut bill: Bill,
    attachments: Vec<AttachDto>,
    assignees: Vec<AssigneeDto>,
) -> BillDto {
    let details = std::mem::take(&mut bill.details);
    let mut dto = base_dto(bill);
    dto.details = details.into_iter().map(detail_dto).collect();
    dto.attachments = attachments;
    dto.assignees = assignees;
    dto
}

/// 服务单模块的权限清单
pub fn permissions() -> Vec<crate::security::model::PermissionDto> {
    use crate::security::model::PermissionDto;
    vec![
        PermissionDto::definition(
            "serviceBill:query",
            "serviceBill",
            "服务单",
            "查看服务单",
            1,
        ),
        PermissionDto::definition(
            "serviceBill:detail",
            "serviceBill",
            "服务单",
            "查看服务单详情",
            2,
        ),
        PermissionDto::definition(
            "serviceBill:create",
            "serviceBill",
            "服务单",
            "新建服务单",
            3,
        ),
        PermissionDto::definition(
            "serviceBill:update",
            "serviceBill",
            "服务单",
            "修改服务单",
            4,
        ),
        PermissionDto::definition(
            "serviceBill:delete",
            "serviceBill",
            "服务单",
            "删除服务单",
            5,
        ),
        PermissionDto::definition(
            "serviceBill:import",
            "serviceBill",
            "服务单",
            "导入服务单",
            6,
        ),
        PermissionDto::definition(
            "serviceBill:export",
            "serviceBill",
            "服务单",
            "导出服务单",
            7,
        ),
        PermissionDto::definition(
            "serviceBill:process",
            "serviceBill",
            "服务单",
            "处理服务单",
            8,
        ),
        PermissionDto::definition(
            "serviceBill:processed",
            "serviceBill",
            "服务单",
            "处理完成服务单",
            9,
        ),
        PermissionDto::definition(
            "serviceBill:finish",
            "serviceBill",
            "服务单",
            "完成服务单",
            10,
        ),
        PermissionDto::definition(
            "serviceBill:cancel-process",
            "serviceBill",
            "服务单",
            "取消处理服务单",
            11,
        ),
        PermissionDto::definition(
            "serviceBill:cancel-processed",
            "serviceBill",
            "服务单",
            "取消处理完成服务单",
            12,
        ),
        PermissionDto::definition(
            "serviceBill:cancel-finish",
            "serviceBill",
            "服务单",
            "取消完成服务单",
            13,
        ),
        PermissionDto::definition(
            "serviceBill:statistic",
            "serviceBill",
            "服务单",
            "统计服务单",
            14,
        ),
        PermissionDto::definition(
            "serviceBill:assign",
            "serviceBill",
            "服务单",
            "指派服务单",
            15,
        ),
        PermissionDto::definition(
            "serviceBill:data-all",
            "serviceBill",
            "服务单",
            "查看全部服务单数据",
            16,
        ),
    ]
}
