package files

import (
	"backend-go/internal/common/errs"

	"github.com/xuri/excelize/v2"
)

// ParseExcel 解析 Excel
func ParseExcel(path string) (*[][]string, error) {
	file, err := excelize.OpenFile(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	sheets := file.GetSheetList()
	if len(sheets) == 0 {
		return nil, errs.NewFileOpError("Excel 为空", path)
	}
	rows, err := file.GetRows(sheets[0])
	if err != nil {
		return nil, err
	}
	var res [][]string
	for _, row := range rows {
		var curRow []string
		for _, colCell := range row {
			curRow = append(curRow, colCell)
		}
		res = append(res, curRow)
	}
	return &res, nil
}

// GenerateExcelFromList 创建 Excel
func GenerateExcelFromList(rows *[][]string, targetPath string) error {
	if rows == nil || len(*rows) == 0 {
		return nil
	}
	if !Exists(targetPath) {
		if err := CreateParentDirs(targetPath); err != nil {
			return err
		}
		if err := CreateFile(targetPath); err != nil {
			return err
		}
	}
	f := excelize.NewFile()
	defer f.Close()
	index, err := f.NewSheet("导出结果")
	if err != nil {
		return err
	}
	// 冻结首行
	if err = f.SetPanes("导出结果", &excelize.Panes{
		YSplit:      1,
		TopLeftCell: "A2",
		ActivePane:  "bottomLeft",
		Freeze:      true,
	}); err != nil {
		return err
	}
	headerStyle, err := f.NewStyle(&excelize.Style{
		Font: &excelize.Font{
			Bold: true,
		},
		Fill: excelize.Fill{
			Type:    "pattern",
			Color:   []string{"#90EE90"},
			Pattern: 1,
		},
	})
	if err != nil {
		return err
	}
	for i, row := range *rows {
		for j, cell := range row {
			cellIndex, err := excelize.CoordinatesToCellName(j+1, i+1)
			if err != nil {
				return err
			}
			// 表头设置样式
			if i == 0 {
				if err = f.SetCellStyle("导出结果", cellIndex, cellIndex, headerStyle); err != nil {
					return err
				}
			}
			if err = f.SetCellValue("导出结果", cellIndex, cell); err != nil {
				return err
			}
		}

	}
	f.SetActiveSheet(index)
	return f.SaveAs(targetPath)
}
