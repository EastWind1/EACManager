package bill

import (
	"backend-go/internal/company"
	"backend-go/pkg/util"
	"context"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// WKMapRule 威垦服务单映射规则
//
// 威垦与菱电共用同一份表单模板，仅识别关键词与公司名不同，因此用字段区分，
// 避免靠结构体嵌入继承行为（Go 的提升方法按静态类型派发，子类型重写的判定不会生效）
type WKMapRule struct {
	companySrc *company.Service
	keyword    string
	maps       map[string]func(*ServiceBillDTO, string)
}

func newWKMapRule(companySrc *company.Service, keyword string) *WKMapRule {
	return &WKMapRule{
		companySrc: companySrc,
		keyword:    keyword,
		maps: map[string]func(*ServiceBillDTO, string){
			"合同编号": func(dto *ServiceBillDTO, value string) { dto.Number = value },
			"下单时间": func(dto *ServiceBillDTO, value string) {
				t, err := util.ParseDateTime(value)
				if err != nil {
					t = new(time.Now())
				}
				dto.OrderDate = t
			},
			"项目名称":  func(dto *ServiceBillDTO, value string) { dto.ProjectName = value },
			"监理、站长": func(dto *ServiceBillDTO, value string) { dto.ProjectContact = value },
			"现场联系人": func(dto *ServiceBillDTO, value string) { dto.OnSiteContact = value },
			"项目地址":  func(dto *ServiceBillDTO, value string) { dto.ProjectAddress = value },
			"备注":    func(dto *ServiceBillDTO, value string) { dto.Remark = value },
		},
	}
}

func (r *WKMapRule) SetByText(target *ServiceBillDTO, text string) {
	if text == "" {
		return
	}
	labels := strings.Split(text, "：")
	if len(labels) != 2 {
		labels = strings.Split(text, ":")
		if len(labels) < 2 {
			return
		}
	}
	if value, ok := r.maps[labels[0]]; ok {
		value(target, labels[1])
	}
}

func (r *WKMapRule) SetCompany(target *ServiceBillDTO, name string) {
	if name == "" {
		return
	}
	companies, err := r.companySrc.FindByName(context.Background(), name)
	if err != nil {
		return
	}
	if len(companies) == 0 {
		return
	}
	target.ProductCompany = new(companies[0])
}

// CanMapTexts 文本块命中关键词
func (r *WKMapRule) CanMapTexts(texts []string) bool {
	if texts == nil {
		return false
	}
	for _, text := range texts {
		if strings.Contains(text, r.keyword) {
			return true
		}
	}
	return false
}

// CanMapGrid 表格命中关键词
func (r *WKMapRule) CanMapGrid(rows [][]string) bool {
	if rows == nil {
		return false
	}
	for _, row := range rows {
		for _, text := range row {
			if strings.Contains(text, r.keyword) {
				return true
			}
		}
	}
	return false
}

func (r *WKMapRule) MapFromTexts(texts []string) (*ServiceBillDTO, bool, error) {
	if !r.CanMapTexts(texts) {
		return nil, false, nil
	}
	dto := ServiceBillDTO{}
	r.SetCompany(&dto, r.keyword)
	for _, text := range texts {
		r.SetByText(&dto, text)
	}
	return &dto, true, nil
}

// matchNumberPattern 检查字符串是否匹配数字模式
func matchNumberPattern(s string) bool {
	matched, _ := regexp.MatchString(`^\d+\.?\d*$`, s)
	return matched
}

func (r *WKMapRule) MapFromGrid(rows [][]string) (*ServiceBillDTO, bool, error) {
	if !r.CanMapGrid(rows) {
		return nil, false, nil
	}

	serviceBill := &ServiceBillDTO{
		Details: []ServiceBillDetailDTO{},
	}

	// 设置公司
	r.SetCompany(serviceBill, r.keyword)

	// 明细开始索引行
	detailStartIndex := -1

	for i, row := range rows {
		// 明细开始
		if len(row) > 0 && strings.Contains(row[0], "序号") {
			detailStartIndex = i + 1
			continue // 跳过表头
		}

		// 明细结束
		if len(row) > 0 && strings.Contains(row[0], "出货信息") {
			detailStartIndex = -1
		}

		// 主表
		if detailStartIndex == -1 {
			for _, text := range row {
				r.SetByText(serviceBill, text)
			}
		} else {
			// 普通列表项, 首个单元格为数字
			if len(row) > 0 && matchNumberPattern(row[0]) {
				detail := ServiceBillDetailDTO{}
				if len(row) > 1 {
					detail.Device = row[1]
				}
				if len(row) > 2 {
					detail.Device += " " + row[2]
				}
				if len(row) > 4 {
					detail.Device += " " + row[4]
				}
				// 去除没有数量的行
				if row[5] == "" {
					continue
				}

				if quantity, err := strconv.ParseFloat(strings.TrimSpace(row[5]), 64); err == nil {
					detail.Quantity = quantity
				}
				if len(row) >= 9 {
					if unitPrice, err := strconv.ParseFloat(strings.TrimSpace(row[7]), 64); err == nil {
						detail.UnitPrice = unitPrice
					}
					if subtotal, err := strconv.ParseFloat(strings.TrimSpace(row[8]), 64); err == nil {
						detail.Subtotal = subtotal
					}
				}

				serviceBill.Details = append(serviceBill.Details, detail)
			} else {
				// 特殊子项
				for j, text := range row {
					if strings.Contains(text, "路") && j+2 < len(row) && row[j+2] != "" {
						detail := ServiceBillDetailDTO{
							Device: "路费补贴",
						}
						if price, err := strconv.ParseFloat(strings.TrimSpace(row[j+2]), 64); err == nil {
							detail.Quantity = 1
							detail.UnitPrice = price
							detail.Subtotal = price
						}
						serviceBill.Details = append(serviceBill.Details, detail)
					}

					if strings.Contains(text, "合计") && j+2 < len(row) {
						if totalAmount, err := strconv.ParseFloat(strings.TrimSpace(row[j+2]), 64); err == nil {
							serviceBill.TotalAmount = totalAmount
						}
					}
				}
			}
		}
	}
	return serviceBill, true, nil
}
