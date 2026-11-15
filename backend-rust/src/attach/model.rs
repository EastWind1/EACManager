use serde::{Deserialize, Serialize};

/// 附件类型，JSON 使用字符串枚举名，与前端 AttachType 一致
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum AttachType {
    Image,
    Pdf,
    Word,
    Excel,
    Other,
}

impl AttachType {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Image => "IMAGE",
            Self::Pdf => "PDF",
            Self::Word => "WORD",
            Self::Excel => "EXCEL",
            Self::Other => "OTHER",
        }
    }

    pub fn from_code(code: i16) -> Self {
        match code {
            0 => Self::Image,
            1 => Self::Pdf,
            2 => Self::Word,
            3 => Self::Excel,
            _ => Self::Other,
        }
    }

    pub fn code(&self) -> i16 {
        match self {
            Self::Image => 0,
            Self::Pdf => 1,
            Self::Word => 2,
            Self::Excel => 3,
            Self::Other => 4,
        }
    }
}

/// 附件状态：TEMP 临时态、TRANSIENT 瞬时态、PERSISTENT 持久态
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AttachStatus {
    Temp,
    Transient,
    Persistent,
}

impl AttachStatus {
    pub fn code(&self) -> i16 {
        match self {
            Self::Temp => 0,
            Self::Transient => 1,
            Self::Persistent => 2,
        }
    }

    pub fn from_code(code: i16) -> Self {
        match code {
            0 => Self::Temp,
            2 => Self::Persistent,
            _ => Self::Transient,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AttachDto {
    #[serde(default)]
    pub id: i32,
    #[serde(default)]
    pub name: String,
    #[serde(rename = "type", default = "default_type")]
    pub attach_type: AttachType,
}

fn default_type() -> AttachType {
    AttachType::Other
}

/// 下载参数：GET /api/attachment/?id=&name=&type=（type 由前端传入，此处不解释）
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AttachParam {
    #[serde(default)]
    pub id: i32,
    #[serde(default)]
    pub name: String,
}

/// 附件模块的权限清单
pub fn permissions() -> Vec<crate::security::model::PermissionDto> {
    use crate::security::model::PermissionDto;
    vec![
        PermissionDto::definition("attach:download", "attach", "附件", "下载附件", 1),
        PermissionDto::definition("attach:upload", "attach", "附件", "上传附件", 2),
    ]
}
