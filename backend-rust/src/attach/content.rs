use crate::attach::model::{AttachDto, AttachType};
use crate::attach::{ocr, office};
use crate::error::{AppError, AppResult};
use crate::state::AppState;

/// 附件内容读取：图片/PDF 取文本块，Excel 取表格。
/// 仅负责把附件变成文本或表格，映射到具体实体由各业务模块完成。
pub async fn texts(app: &AppState, attachment: &AttachDto) -> AppResult<Vec<String>> {
    let path = crate::attach::service::absolute_path_by_id(app, attachment.id).await?;
    match attachment.attach_type {
        AttachType::Image => ocr::parse_image(&app.config.ocr.url, &path).await,
        AttachType::Pdf => {
            let lines = ocr_free_pdf(&path);
            if lines.is_empty() {
                ocr::parse_image(&app.config.ocr.url, &path).await
            } else {
                Ok(lines)
            }
        }
        _ => Err(AppError::Biz("不支持解析为文本的附件类型".into())),
    }
}

fn ocr_free_pdf(path: &std::path::Path) -> Vec<String> {
    match ocr::extract_pdf_text(path) {
        Ok(lines) => lines,
        Err(error) => {
            tracing::warn!("PDF 文本层解析失败: {error}");
            Vec::new()
        }
    }
}

pub async fn grid(app: &AppState, attachment: &AttachDto) -> AppResult<Vec<Vec<String>>> {
    if attachment.attach_type != AttachType::Excel {
        return Err(AppError::Biz("不支持解析为表格的附件类型".into()));
    }
    let path = crate::attach::service::absolute_path_by_id(app, attachment.id).await?;
    office::parse_excel(&path)
}
