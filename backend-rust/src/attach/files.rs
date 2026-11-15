use std::io::{Read, Write};
use std::path::{Path, PathBuf};

use axum::body::Body;
use axum::http::{HeaderValue, StatusCode, header};
use axum::response::Response;

use crate::error::{AppError, AppResult};
use crate::util::{ensure_within, safe_join};

/// 可执行文件等危险类型统一拒绝
pub fn detect_type(path: &Path) -> AppResult<crate::attach::model::AttachType> {
    use crate::attach::model::AttachType;
    let kind = infer::get_from_path(path)
        .map_err(|e| AppError::FileOp(format!("获取文件类型失败: {e}")))?;
    let Some(kind) = kind else {
        return Ok(AttachType::Other);
    };
    let mime = kind.mime_type();
    let attach_type = match mime {
        "application/pdf" => AttachType::Pdf,
        "image/jpeg" | "image/png" | "image/gif" => AttachType::Image,
        "application/msword"
        | "application/vnd.openxmlformats-officedocument.wordprocessingml.document" => {
            AttachType::Word
        }
        "application/vnd.ms-excel"
        | "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" => AttachType::Excel,
        "application/x-msdownload"
        | "application/x-executable"
        | "application/x-sh"
        | "application/x-bat" => {
            return Err(AppError::FileOp("不支持的文件类型".into()));
        }
        _ => AttachType::Other,
    };
    Ok(attach_type)
}

/// 下载响应的 Content-Type：按扩展名推断，未知类型退回二进制流
pub fn content_type(name: &str) -> &'static str {
    let extension = Path::new(name)
        .extension()
        .and_then(|value| value.to_str())
        .unwrap_or_default()
        .to_ascii_lowercase();
    match extension.as_str() {
        "pdf" => "application/pdf",
        "png" => "image/png",
        "jpg" | "jpeg" => "image/jpeg",
        "gif" => "image/gif",
        "webp" => "image/webp",
        "doc" => "application/msword",
        "docx" => "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" => "application/vnd.ms-excel",
        "xlsx" => "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "txt" => "text/plain; charset=utf-8",
        "zip" => "application/zip",
        _ => "application/octet-stream",
    }
}

pub fn create_parent_dirs(path: &Path) -> AppResult<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    Ok(())
}

pub fn exists(path: &Path) -> bool {
    path.exists()
}

pub fn delete_quietly(path: &Path) {
    if let Err(error) = std::fs::remove_file(path) {
        if error.kind() != std::io::ErrorKind::NotFound {
            tracing::error!("删除附件文件失败 {}: {error}", path.display());
        }
    }
}

pub fn copy_file(src: &Path, dst: &Path) -> AppResult<()> {
    create_parent_dirs(dst)?;
    std::fs::copy(src, dst).map_err(|e| {
        AppError::FileOp(format!(
            "复制文件失败 {} -> {}: {e}",
            src.display(),
            dst.display()
        ))
    })?;
    Ok(())
}

pub fn move_file(src: &Path, dst: &Path) -> AppResult<()> {
    copy_file(src, dst)?;
    std::fs::remove_file(src).map_err(|e| AppError::FileOp(format!("删除源文件失败: {e}")))?;
    Ok(())
}

/// 打包目录为 zip；target 为空时落到系统临时目录
pub fn zip_dir(src: &Path, target: Option<&Path>) -> AppResult<PathBuf> {
    if !src.exists() {
        return Err(AppError::FileOp("源不存在".into()));
    }
    let target_path = match target {
        Some(path) => {
            create_parent_dirs(path)?;
            path.to_path_buf()
        }
        None => std::env::temp_dir().join(format!("export-{}.zip", crate::util::gen_int_id())),
    };
    let file = std::fs::File::create(&target_path)?;
    let mut writer = zip::ZipWriter::new(file);
    let options = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated);
    let mut stack = vec![src.to_path_buf()];
    while let Some(dir) = stack.pop() {
        for entry in std::fs::read_dir(&dir)? {
            let entry = entry?;
            let path = entry.path();
            let relative = path
                .strip_prefix(src)
                .map_err(|_| AppError::FileOp("路径不合法".into()))?;
            let name = relative.to_string_lossy().replace('\\', "/");
            if path.is_dir() {
                writer.add_directory(format!("{name}/"), options)?;
                stack.push(path);
            } else {
                writer.start_file(name, options)?;
                let mut input = std::fs::File::open(&path)?;
                let mut buffer = Vec::new();
                input.read_to_end(&mut buffer)?;
                writer.write_all(&buffer)?;
            }
        }
    }
    writer.finish()?;
    Ok(target_path)
}

/// 导出目录内的同名附件：name、1-name、2-name ……
pub fn unique_target_path(dir: &Path, name: &str) -> AppResult<PathBuf> {
    let mut candidate = safe_join(dir, name)?;
    let mut repeat = 1;
    while exists(&candidate) {
        candidate = safe_join(dir, &format!("{repeat}-{name}"))?;
        repeat += 1;
    }
    Ok(candidate)
}

/// 校验 target 位于 base 内；供导出等业务侧复用
pub fn assert_within(base: &Path, target: &Path) -> AppResult<()> {
    ensure_within(base, target)
}
/// 附件/导出文件的下载响应：文件名按 UTF-8 百分号编码，保证中文名在各浏览器一致
pub fn file_response(path: &Path, name: &str) -> AppResult<Response> {
    let bytes =
        std::fs::read(path).map_err(|error| AppError::FileOp(format!("读取文件失败: {error}")))?;
    let encoded = encode_filename(name);
    let disposition = format!("attachment; filename=\"{encoded}\"; filename*=UTF-8''{encoded}");
    let mut response = Response::new(Body::from(bytes));
    *response.status_mut() = StatusCode::OK;
    let headers = response.headers_mut();
    headers.insert(
        header::CONTENT_TYPE,
        HeaderValue::from_static("application/octet-stream"),
    );
    if let Ok(value) = HeaderValue::from_str(&disposition) {
        headers.insert(header::CONTENT_DISPOSITION, value);
    }
    Ok(response)
}

/// 下载响应的 Content-Disposition 用 UTF-8 百分号编码
pub fn encode_filename(name: &str) -> String {
    let mut out = String::new();
    for byte in name.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(byte as char)
            }
            _ => out.push_str(&format!("%{byte:02X}")),
        }
    }
    out
}
