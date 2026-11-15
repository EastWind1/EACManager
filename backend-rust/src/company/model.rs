use serde::{Deserialize, Serialize};

use crate::error::{AppError, AppResult};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompanyDto {
    pub id: i32,
    pub name: String,
    pub contact_name: String,
    pub contact_phone: String,
    pub email: String,
    pub address: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub disabled: Option<bool>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompanyRequest {
    #[serde(default)]
    pub id: i32,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub contact_name: Option<String>,
    #[serde(default)]
    pub contact_phone: Option<String>,
    #[serde(default)]
    pub email: Option<String>,
    #[serde(default)]
    pub address: Option<String>,
}

impl CompanyRequest {
    /// 借出字段切片，校验与写库都只需读取
    pub fn contact_name(&self) -> &str {
        self.contact_name.as_deref().unwrap_or_default().trim()
    }

    pub fn contact_phone(&self) -> &str {
        self.contact_phone.as_deref().unwrap_or_default().trim()
    }

    pub fn email(&self) -> &str {
        self.email.as_deref().unwrap_or_default().trim()
    }

    pub fn address(&self) -> &str {
        self.address.as_deref().unwrap_or_default().trim()
    }

    pub fn validate(&self) -> AppResult<()> {
        let name = self.name.trim();
        if name.is_empty() {
            return Err(AppError::Biz("公司名称不能为空".into()));
        }
        if name.chars().count() > 100 {
            return Err(AppError::Biz("公司名称不能超过 100 个字符".into()));
        }
        if self.contact_name().chars().count() > 64 {
            return Err(AppError::Biz("联系人不能超过 64 个字符".into()));
        }
        let phone = self.contact_phone();
        if !phone.is_empty() && !crate::auth::PHONE_PATTERN.is_match(phone) {
            return Err(AppError::Biz("联系电话格式不正确".into()));
        }
        if self.email().chars().count() > 128 {
            return Err(AppError::Biz("邮箱不能超过 128 个字符".into()));
        }
        if !self.email().is_empty() {
            crate::security::model::validate_email(self.email())?;
        }
        if self.address().chars().count() > 255 {
            return Err(AppError::Biz("地址不能超过 255 个字符".into()));
        }
        Ok(())
    }
}

/// 公司模块的权限清单
pub fn permissions() -> Vec<crate::security::model::PermissionDto> {
    use crate::security::model::PermissionDto;
    vec![
        PermissionDto::definition("company:query", "company", "公司", "查看公司", 1),
        PermissionDto::definition("company:create", "company", "公司", "新建公司", 2),
        PermissionDto::definition("company:update", "company", "公司", "修改公司", 3),
        PermissionDto::definition("company:disable", "company", "公司", "禁用公司", 4),
    ]
}
