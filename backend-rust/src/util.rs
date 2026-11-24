use std::path::{Component, Path, PathBuf};
use std::sync::Mutex;
use std::sync::atomic::{AtomicU32, Ordering};

use chrono::{DateTime, Local, NaiveDate, NaiveDateTime, TimeZone, Utc};

use crate::error::{AppError, AppResult};

static SEQ: AtomicU32 = AtomicU32::new(1);
static PRE_TIME_BIT: Mutex<u32> = Mutex::new(0);

/// 生成时间有序 int32 ID（雪花算法）：26 位毫秒时间左移 5 位，再或上 1..31 的序列
pub fn gen_int_id() -> i32 {
    let time_bit = ((Utc::now().timestamp_millis() as u64 & 0x3F_FFFF) << 5) as u32;
    let mut pre = PRE_TIME_BIT.lock().expect("lock");
    let mut seq = SEQ.fetch_add(1, Ordering::SeqCst) + 1;
    if *pre != time_bit || seq >= 1 << 5 {
        seq = 1;
        SEQ.store(seq, Ordering::SeqCst);
    }
    *pre = time_bit;
    (time_bit | seq) as i32
}

pub fn camel_to_snake(value: &str) -> String {
    if value.is_empty() {
        return String::new();
    }
    let mut out = String::with_capacity(value.len() + 4);
    for (index, ch) in value.chars().enumerate() {
        if ch.is_uppercase() {
            if index != 0 {
                out.push('_');
            }
            out.extend(ch.to_lowercase());
        } else {
            out.push(ch);
        }
    }
    out
}

/// 文本日期解析：依次尝试 ISO、中文日期等格式，解析失败返回 None
pub fn parse_datetime(text: &str) -> Option<DateTime<Utc>> {
    let mut processed = text.trim().to_string();
    if processed.is_empty() {
        return None;
    }
    processed = processed
        .replace(['年', '月'], "-")
        .replace('日', " ")
        .replace(['时', '分'], ":")
        .replace('秒', "");
    let processed = collapse_separator(&processed);
    let processed = processed.trim().to_string();

    if let Ok(parsed) = DateTime::parse_from_rfc3339(text.trim()) {
        return Some(parsed.with_timezone(&Utc));
    }
    for format in [
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%dT%H:%M",
        "%Y/%m/%d %H:%M:%S",
        "%Y/%m/%d %H:%M",
    ] {
        if let Ok(value) = NaiveDateTime::parse_from_str(&processed, format) {
            return Some(to_utc(value.and_utc()));
        }
    }
    for format in ["%Y-%m-%d", "%Y/%m/%d"] {
        if let Ok(value) = NaiveDate::parse_from_str(&processed, format) {
            return Some(to_utc(value.and_hms_opt(0, 0, 0)?.and_utc()));
        }
    }
    None
}

fn to_utc(value: DateTime<Utc>) -> DateTime<Utc> {
    value
}

fn collapse_separator(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    let mut last_dash = false;
    let mut last_colon = false;
    for ch in value.chars() {
        if ch == '-' {
            if last_dash {
                continue;
            }
            last_dash = true;
        } else {
            last_dash = false;
        }
        if ch == ':' {
            if last_colon {
                continue;
            }
            last_colon = true;
        } else {
            last_colon = false;
        }
        out.push(ch);
    }
    out
}

/// 解析查询用的日期边界；只有日期时按本地时区的当天起点或终点
pub fn parse_bound(text: &str, end_of_day: bool) -> Option<DateTime<Utc>> {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return None;
    }
    if let Ok(parsed) = DateTime::parse_from_rfc3339(trimmed) {
        return Some(parsed.with_timezone(&Utc));
    }
    for format in ["%Y-%m-%d", "%Y/%m/%d"] {
        if let Ok(date) = NaiveDate::parse_from_str(trimmed, format) {
            let time = if end_of_day { (23, 59, 59) } else { (0, 0, 0) };
            let naive = date.and_hms_opt(time.0, time.1, time.2)?;
            let local = Local.from_local_datetime(&naive).single()?;
            return Some(local.with_timezone(&Utc));
        }
    }
    parse_datetime(trimmed)
}

pub fn now() -> DateTime<Utc> {
    Utc::now()
}

/// 金额在进入实体前统一保留两位小数。
pub fn round_money(value: f64) -> f64 {
    (value * 100.0).round() / 100.0
}

/// 原始文件名校验：拒绝 <>:"/\|?*、单独的 .. 和空名
pub fn valid_name(name: &str) -> AppResult<()> {
    if name.is_empty() {
        return Err(AppError::Biz("文件名为空".into()));
    }
    if name
        .chars()
        .any(|c| matches!(c, '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*'))
    {
        return Err(AppError::Biz("文件名不能包含 <>:\"/\\|?*".into()));
    }
    if name == ".." {
        return Err(AppError::Biz("文件名不能为 ..".into()));
    }
    Ok(())
}

/// 校验拼接结果仍在 base 目录内（含 base 自身）
pub fn ensure_within(base: &Path, target: &Path) -> AppResult<()> {
    let base = normalize(base);
    let target = normalize(target);
    if !target.starts_with(&base) {
        return Err(AppError::Biz("路径不合法".into()));
    }
    Ok(())
}

pub fn safe_join(base: &Path, name: &str) -> AppResult<PathBuf> {
    if name.is_empty() {
        return Err(AppError::Biz("路径不能为空".into()));
    }
    let candidate = Path::new(name);
    if candidate.is_absolute()
        || candidate.components().any(|c| {
            matches!(
                c,
                Component::ParentDir | Component::RootDir | Component::Prefix(_)
            )
        })
    {
        return Err(AppError::Biz("路径不合法".into()));
    }
    let target = normalize(&base.join(candidate));
    ensure_within(base, &target)?;
    Ok(target)
}

fn normalize(path: &Path) -> PathBuf {
    let mut out = PathBuf::new();
    for component in path.components() {
        match component {
            Component::ParentDir => {
                out.pop();
            }
            Component::CurDir => {}
            other => out.push(other.as_os_str()),
        }
    }
    out
}

/// 单号校验：拒绝路径分隔符和单独的 ..
pub fn valid_number(number: &str) -> AppResult<()> {
    if number.trim().is_empty() {
        return Err(AppError::Biz("单号为空".into()));
    }
    if number.contains('/') || number.contains('\\') {
        return Err(AppError::Biz("单号不能包含 / \\".into()));
    }
    if number == ".." {
        return Err(AppError::Biz("单号不能为 ..".into()));
    }
    Ok(())
}

pub fn unique_ids(values: &[i32]) -> Vec<i32> {
    let mut seen = std::collections::HashSet::new();
    let mut out = Vec::with_capacity(values.len());
    for value in values {
        if *value == 0 || !seen.insert(*value) {
            continue;
        }
        out.push(*value);
    }
    out
}

pub fn same_ids(a: &[i32], b: &[i32]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let set: std::collections::HashSet<_> = a.iter().collect();
    b.iter().all(|value| set.contains(value))
}
