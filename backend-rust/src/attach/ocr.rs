use std::path::Path;

use crate::error::{AppError, AppResult};

#[derive(serde::Deserialize)]
struct OcrResponse {
    #[serde(default)]
    code: i32,
    #[serde(default)]
    msg: String,
    #[serde(default)]
    data: Vec<String>,
}

/// 调用独立 OCR 服务（FastAPI）：POST multipart file，返回文本块
pub async fn parse_image(url: &str, path: &Path) -> AppResult<Vec<String>> {
    if url.is_empty() {
        return Err(AppError::Biz("未配置 OCR 服务器".into()));
    }
    let bytes = std::fs::read(path)?;
    let name = path
        .file_name()
        .map(|name| name.to_string_lossy().to_string())
        .unwrap_or_else(|| "image".to_string());
    let part = reqwest::multipart::Part::bytes(bytes)
        .file_name(name.clone())
        .mime_str("application/octet-stream")
        .map_err(|e| AppError::Internal(e.to_string()))?;
    let form = reqwest::multipart::Form::new().part("file", part);
    let started = std::time::Instant::now();
    let client = reqwest::Client::new();
    let response = client.post(url).multipart(form).send().await.map_err(|e| {
        tracing::error!("OCR 调用失败: {url} - {e}");
        AppError::Internal(format!("OCR 调用失败: {e}"))
    })?;
    let status = response.status();
    let body = response.text().await.unwrap_or_default();
    tracing::info!(
        "OCR 调用完成: {name} 耗时 {}ms",
        started.elapsed().as_millis()
    );
    let parsed: OcrResponse = serde_json::from_str(&body).unwrap_or(OcrResponse {
        code: status.as_u16() as i32,
        msg: body.clone(),
        data: Vec::new(),
    });
    if !status.is_success() || parsed.code != 0 {
        return Err(AppError::Biz(if parsed.msg.is_empty() {
            "OCR 识别失败".into()
        } else {
            parsed.msg
        }));
    }
    Ok(parsed.data)
}

/// 读取 PDF 文本层；没有文本层时调用方再降级 OCR
pub fn extract_pdf_text(path: &Path) -> AppResult<Vec<String>> {
    let raw = pdf_extract::extract_text(path)
        .map_err(|e| AppError::FileOp(format!("PDF 打开失败: {e}")))?;
    Ok(raw
        .lines()
        .map(|line| line.replace('\u{fffd}', "").trim_end().to_string())
        .filter(|line| !line.is_empty())
        .collect())
}
