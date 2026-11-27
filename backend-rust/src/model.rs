use std::collections::HashMap;

use axum::Json;
use serde::{Deserialize, Serialize};
use serde_json::Value;

/// 统一响应体：成功与否由 HTTP 状态码表达，`message` 固定为 OK。
#[derive(Debug, Serialize)]
pub struct ResultBody {
    pub message: &'static str,
    pub data: Value,
}

pub fn ok<T: Serialize>(data: T) -> Json<ResultBody> {
    Json(ResultBody {
        message: "OK",
        data: serde_json::to_value(data).unwrap_or(Value::Null),
    })
}

/// 无返回值的写动作
pub fn ok_empty() -> Json<ResultBody> {
    Json(ResultBody {
        message: "OK",
        data: Value::Null,
    })
}

/// 分页结果，pageIndex 从 0 开始
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PageResult<T> {
    pub items: Vec<T>,
    pub total_count: i64,
    pub total_pages: i64,
    pub page_size: i64,
    pub page_index: i64,
}

impl<T> PageResult<T> {
    pub fn new(items: Vec<T>, total_count: i64, page: &QueryParam) -> Self {
        let page_size = page.page_size();
        let total_pages = if page_size > 0 {
            (total_count + page_size - 1) / page_size
        } else {
            0
        };
        Self {
            items,
            total_count,
            total_pages,
            page_size,
            page_index: page.page_index(),
        }
    }

    /// 只需要条数与分页信息时，避免构造占位元素
    pub fn empty(page: &QueryParam) -> Self {
        Self::new(Vec::new(), 0, page)
    }
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
pub struct SortParam {
    #[serde(default)]
    pub field: String,
    #[serde(default)]
    pub direction: Option<String>,
}

/// 公共查询参数：两个分页参数都为空时不分页，只给其中一个视为参数错误。
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct QueryParam {
    pub page_size: Option<i64>,
    pub page_index: Option<i64>,
    pub sorts: Option<Vec<SortParam>>,
}

/// 单页条数上限，超出时按上限裁剪。
pub const MAX_PAGE_SIZE: i64 = 200;

impl QueryParam {
    pub fn validate(&self) -> Result<(), String> {
        match (self.page_size, self.page_index) {
            (Some(size), Some(index)) => {
                if size < 1 || index < 0 {
                    return Err("分页参数不合法，索引必须非负，页大小必须大于0".into());
                }
            }
            (None, None) => {}
            _ => return Err("分页参数不能只有一个为空".into()),
        }
        for sort in self.sorts.as_deref().unwrap_or_default() {
            if sort.field.trim().is_empty() {
                return Err("排序字段不能为空".into());
            }
            if !matches!(Self::direction(sort), "ASC" | "DESC") {
                return Err("排序方向只能是asc或desc".into());
            }
        }
        Ok(())
    }

    fn direction(sort: &SortParam) -> &'static str {
        match sort.direction.as_deref().map(str::trim) {
            Some(value) if value.eq_ignore_ascii_case("desc") => "DESC",
            _ => "ASC",
        }
    }

    pub fn has_page(&self) -> bool {
        self.page_size.is_some() && self.page_index.is_some()
    }

    pub fn offset(&self) -> i64 {
        match (self.page_index, self.page_size) {
            (Some(index), Some(_)) => index * self.page_size(),
            _ => 0,
        }
    }

    pub fn page_index(&self) -> i64 {
        self.page_index.unwrap_or(0)
    }

    /// 未分页时返回 -1，调用方据此决定是否追加 LIMIT
    pub fn page_size(&self) -> i64 {
        self.page_size
            .map(|size| size.min(MAX_PAGE_SIZE))
            .unwrap_or(-1)
    }

    /// 生成 ORDER BY 子句。字段名按驼峰转下划线并做标识符白名单校验，避免拼接注入。
    pub fn order_by(&self) -> Result<String, String> {
        let mut parts = Vec::new();
        for sort in self.sorts.as_deref().unwrap_or_default() {
            let column = crate::util::camel_to_snake(sort.field.trim());
            let valid = !column.is_empty()
                && column
                    .chars()
                    .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_');
            if !valid {
                return Err("排序字段不合法".into());
            }
            parts.push(format!("\"{column}\" {}", Self::direction(sort)));
        }
        Ok(parts.join(", "))
    }
}

/// 批量操作结果：单项失败不影响其他项，由前端逐条提示。
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ActionsResult<P> {
    pub results: Vec<ActionRow<P>>,
    pub success_count: i64,
    pub fail_count: i64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ActionRow<P> {
    pub param: P,
    pub result: Value,
    pub success: bool,
    pub message: String,
}

impl<P> ActionRow<P> {
    pub fn success(param: P) -> Self {
        Self {
            param,
            result: Value::Null,
            success: true,
            message: String::new(),
        }
    }

    pub fn failure(param: P, message: impl Into<String>) -> Self {
        Self {
            param,
            result: Value::Null,
            success: false,
            message: message.into(),
        }
    }
}

impl<P> ActionsResult<P> {
    pub fn new(results: Vec<ActionRow<P>>) -> Self {
        let success_count = results.iter().filter(|row| row.success).count() as i64;
        let fail_count = results.len() as i64 - success_count;
        Self {
            results,
            success_count,
            fail_count,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MonthSumAmount {
    pub month: String,
    pub amount: f64,
}

pub type CountByState = HashMap<String, i64>;

#[derive(Debug, Default, Serialize)]
pub struct InitStatus {
    pub required: bool,
}
