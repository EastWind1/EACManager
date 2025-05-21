package pers.eastwind.billmanager.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.model.dto.ImportMapRule;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Word、Excel 文件处理
 */
@Service
public class OfficeFileService {
    private final MapRuleService mapRuleService;
    private final AttachmentService attachmentService;

    public OfficeFileService(MapRuleService mapRuleService, AttachmentService attachmentService) {
        this.mapRuleService = mapRuleService;
        this.attachmentService = attachmentService;

    }

    /**
     * 将 Excel 转换至对象
     *
     * @param path 文件相对路径
     * @param target           目标对象
     */
    public void parseExcel(Path path, Object target) {
        List<ImportMapRule> mapRules = mapRuleService.getMapRule("Excel", target.getClass().getSimpleName());

        List<String> texts = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(path.toFile())) {
            // 只读取第一个 sheet
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new RuntimeException("文档为空");
            }
            for (Row row : sheet) {
                for (Cell cell : row) {
                    texts.add(cell.getStringCellValue());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("打开文档失败", e);
        }
        mapRuleService.executeMapRule(mapRules, texts, target);
    }

    /**
     * 生成Excel文件
     * @param rows 数据
     * @param targetFile 目标文件
     *
     */
    public Path generateExcelFromList(List<List<String>> rows, Path targetFile) {
        if ( rows == null || rows.isEmpty()) {
            throw new RuntimeException("数据不能为空");
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
            throw new RuntimeException("生成Excel失败", e);
        }
        return targetFile;
    }

    /**
     * 生成Excel文件
     * @param rows 数据
     *
     */
    public Path generateExcelFromList(List<List<String>> rows) {
        Path path = attachmentService.createFile(attachmentService.getTempPath().resolve("Generate-" + System.currentTimeMillis() + ".xlsx"));
        generateExcelFromList(rows, path);
        return path;
    }
}
