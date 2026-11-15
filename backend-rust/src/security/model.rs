use serde::{Deserialize, Serialize};

use crate::error::{AppError, AppResult};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RoleDto {
    #[serde(default)]
    pub id: i32,
    #[serde(default)]
    pub code: String,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub sort_order: i32,
    #[serde(default)]
    pub disabled: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PermissionDto {
    pub id: i32,
    pub code: String,
    pub module: String,
    pub module_name: String,
    pub name: String,
    pub sort_order: i32,
}

impl PermissionDto {
    pub fn definition(
        code: &str,
        module: &str,
        module_name: &str,
        name: &str,
        sort_order: i32,
    ) -> Self {
        Self {
            id: 0,
            code: code.into(),
            module: module.into(),
            module_name: module_name.into(),
            name: name.into(),
            sort_order,
        }
    }
}

/// 用户接口响应：账户 + 角色权限 + 资料字段
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UserDto {
    pub id: i32,
    pub username: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub password: Option<String>,
    pub disabled: bool,
    pub roles: Vec<RoleDto>,
    pub permissions: Vec<PermissionDto>,
    pub name: String,
    pub phone: String,
    pub email: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LoginParam {
    #[serde(default)]
    pub username: String,
    #[serde(default)]
    pub password: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct UserRequest {
    #[serde(default)]
    pub id: i32,
    #[serde(default)]
    pub username: String,
    #[serde(default)]
    pub password: Option<String>,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub phone: Option<String>,
    #[serde(default)]
    pub email: Option<String>,
    #[serde(default)]
    pub roles: Option<Vec<RoleDto>>,
}

impl UserRequest {
    pub fn password_or_empty(&self) -> &str {
        self.password.as_deref().unwrap_or("")
    }

    pub fn name_or_empty(&self) -> &str {
        self.name.as_deref().unwrap_or("").trim()
    }

    pub fn phone_or_empty(&self) -> &str {
        self.phone.as_deref().unwrap_or("").trim()
    }

    pub fn email_or_empty(&self) -> &str {
        self.email.as_deref().unwrap_or("").trim()
    }
}

#[derive(Debug, Clone)]
pub struct LoginResult {
    pub token: String,
    pub account: UserDto,
}

pub fn role_codes(roles: &[RoleDto]) -> Vec<String> {
    roles.iter().map(|role| role.code.clone()).collect()
}

pub fn validate_profile_name(name: &str) -> AppResult<()> {
    if name.trim().is_empty() {
        return Err(AppError::Biz("姓名不能为空".into()));
    }
    if name.chars().count() > 64 {
        return Err(AppError::Biz("姓名不能超过 64 个字符".into()));
    }
    Ok(())
}

pub fn validate_phone(phone: &str) -> AppResult<()> {
    if phone.is_empty() || crate::auth::PHONE_PATTERN.is_match(phone) {
        return Ok(());
    }
    Err(AppError::Biz("手机号格式不正确".into()))
}

pub fn validate_email(email: &str) -> AppResult<()> {
    if email.is_empty() {
        return Ok(());
    }
    if email.chars().count() > 128 {
        return Err(AppError::Biz("邮箱不能超过 128 个字符".into()));
    }
    let valid = email.len() >= 5
        && email.matches('@').count() == 1
        && !email.starts_with('@')
        && !email.ends_with('@')
        && email.contains('.')
        && !email.contains(' ');
    if !valid {
        return Err(AppError::Biz("邮箱格式不正确".into()));
    }
    Ok(())
}
