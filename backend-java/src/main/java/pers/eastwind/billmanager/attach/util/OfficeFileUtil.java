package pers.eastwind.billmanager.attach.util;

import org.apache.commons.io.file.PathUtils;
import org.apache.poi.ss.usermodel.*;
import pers.eastwind.billmanager.common.exception.BizException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Word、Excel 文件处理
 */
public class OfficeFileUtil {
    /**
     * 读取 Excel 文件, 范围二维数据
     *
     * @param path   文件相对路径
     */
    public static List<List<String>> parseExcel(Path path) {
        List<List<String>> res = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(path.toFile())) {
            // 只读取第一个 sheet
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BizException("文档为空");
            }
            for (Row row : sheet) {
                // 跳过隐藏行
                if (row.getZeroHeight()) {
                    continue;
                }
                List<String> curRow = new ArrayList<>();
                for (Cell cell : row) {
                    switch (cell.getCellType()) {
                        case STRING -> curRow.add(cell.getStringCellValue());
                        case NUMERIC -> curRow.add(String.valueOf(cell.getNumericCellValue()));
                        case BLANK -> curRow.add("");
                        case FORMULA -> {
                            // 使用公式缓存值
                            switch (cell.getCachedFormulaResultType()) {
                                case STRING -> curRow.add(cell.getStringCellValue());
                                case NUMERIC -> curRow.add(String.valueOf(cell.getNumericCellValue()));
                                case BLANK -> curRow.add("");
                                default -> throw new BizException("不支持的 Cell 类型");
                            }
                        }
                        default -> throw new BizException("不支持的 Cell 类型");
                    }
                }
                res.add(curRow);
            }
        } catch (IOException e) {
            throw new BizException("打开文档失败, 若文件过大请尝试瘦身文件", e);
        }
        return res;
    }

    /**
     * 生成 Excel 文件
     * 只支持简单的二维数据
     * @param rows       数据
     * @param targetFile 目标文件, 若存在则替换
     */
    public static void generateExcelFromList(List<List<String>> rows, Path targetFile) {
        if (rows == null || rows.isEmpty()) {
            throw new BizException("数据不能为空");
        }
        if (!Files.exists(targetFile)) {
            try {
                PathUtils.createParentDirectories(targetFile);
                Files.createFile(targetFile);
            } catch (IOException e) {
                throw new BizException("创建 Excel 失败", e);
            }
        }
        try (Workbook workbook = WorkbookFactory.create(true)) {
            Sheet sheet = workbook.createSheet("导出结果");

            //表头样式，背景色，加粗字体
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font font = workbook.createFont();
            font.setBold(true); // 字体加粗
            headerStyle.setFont(font);

            //冻结首行
            sheet.createFreezePane(0, 1);

            for (int i = 0; i < rows.size(); i++) {
                List<String> rowData = rows.get(i);
                Row row = sheet.createRow(i);
                for (int j = 0; j < rowData.size(); j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(rowData.get(j));
                    if (i == 0) {
                        cell.setCellStyle(headerStyle);
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {
                workbook.write(fos);
            }
        } catch (IOException e) {
            throw new BizException("写入 Excel 失败", e);
        }
    }
}
