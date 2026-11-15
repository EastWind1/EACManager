use std::path::Path;

use calamine::{Data, Reader, open_workbook_auto};
use rust_xlsxwriter::{Format, FormatAlign, Workbook};

use crate::error::{AppError, AppResult};

/// 读取 Excel 首个工作表的全部行
pub fn parse_excel(path: &Path) -> AppResult<Vec<Vec<String>>> {
    let mut workbook =
        open_workbook_auto(path).map_err(|e| AppError::FileOp(format!("Excel 打开失败: {e}")))?;
    let sheets = workbook.sheet_names().to_vec();
    let Some(sheet) = sheets.first() else {
        return Err(AppError::FileOp("Excel 为空".into()));
    };
    let range = workbook
        .worksheet_range(sheet)
        .map_err(|e| AppError::FileOp(format!("Excel 读取失败: {e}")))?;
    Ok(range
        .rows()
        .map(|row| row.iter().map(cell_to_string).collect())
        .collect())
}

fn cell_to_string(cell: &Data) -> String {
    match cell {
        Data::Empty => String::new(),
        Data::String(value) => value.clone(),
        Data::Float(value) => {
            if value.fract() == 0.0 {
                format!("{}", *value as i64)
            } else {
                value.to_string()
            }
        }
        Data::Int(value) => value.to_string(),
        Data::Bool(value) => value.to_string(),
        Data::DateTime(value) => value.to_string(),
        Data::DateTimeIso(value) => value.clone(),
        Data::DurationIso(value) => value.clone(),
        Data::Error(value) => format!("{value:?}"),
    }
}

/// 生成导出结果表：首行加粗填充、冻结首行
pub fn generate_excel(rows: &[Vec<String>], target: &Path) -> AppResult<()> {
    if rows.is_empty() {
        return Ok(());
    }
    crate::attach::files::create_parent_dirs(target)?;
    let mut workbook = Workbook::new();
    let sheet = workbook.add_worksheet();
    sheet
        .set_name("导出结果")
        .map_err(|e| AppError::FileOp(e.to_string()))?;
    sheet
        .set_freeze_panes(1, 0)
        .map_err(|e| AppError::FileOp(e.to_string()))?;
    let header = Format::new()
        .set_bold()
        .set_background_color(rust_xlsxwriter::Color::RGB(0x90EE90))
        .set_align(FormatAlign::Left);
    for (row_index, row) in rows.iter().enumerate() {
        for (col_index, cell) in row.iter().enumerate() {
            let row_number = row_index as u32;
            let col_number = col_index as u16;
            if row_index == 0 {
                sheet
                    .write_string_with_format(row_number, col_number, cell, &header)
                    .map_err(|e| AppError::FileOp(e.to_string()))?;
            } else {
                sheet
                    .write_string(row_number, col_number, cell)
                    .map_err(|e| AppError::FileOp(e.to_string()))?;
            }
        }
    }
    workbook
        .save(target)
        .map_err(|e| AppError::FileOp(e.to_string()))?;
    Ok(())
}
