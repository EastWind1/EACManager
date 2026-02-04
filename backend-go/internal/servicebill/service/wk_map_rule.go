package service

import (
	"backend-go/internal/common/errs"
	"backend-go/internal/common/util"
	"backend-go/internal/company/service"
	"backend-go/internal/servicebill/model"
	"context"
	"regexp"
	"strconv"
	"strings"
	"time"
)

type WKMapRule struct {
	companySrc *service.CompanyService
	maps       map[string]func(*model.ServiceBillDTO, string)
}

func NewWKMapRule(companySrc *service.CompanyService) *WKMapRule {
	return &WKMapRule{
		companySrc: companySrc,
		maps: map[string]func(*model.ServiceBillDTO, string){
			"合同编号": func(dto *model.ServiceBillDTO, value string) { dto.Number = value },
			"下单时间": func(dto *model.ServiceBillDTO, value string) {
				t, err := util.ParseDateTime(value)
				if err != nil {
					now := time.Now()
					t = &now
				}
				dto.OrderDate = t
			},
			"项目名称":  func(dto *model.ServiceBillDTO, value string) { dto.ProjectName = value },
			"监理、站长": func(dto *model.ServiceBillDTO, value string) { dto.ProjectContact = value },
			"现场联系人": func(dto *model.ServiceBillDTO, value string) { dto.OnSiteContact = value },
			"项目地址":  func(dto *model.ServiceBillDTO, value string) { dto.ProjectAddress = value },
			"备注":    func(dto *model.ServiceBillDTO, value string) { dto.Remark = value },
		},
	}
}

func (r *WKMapRule) SetByText(target *model.ServiceBillDTO, text string) {
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

func (r *WKMapRule) SetCompany(target *model.ServiceBillDTO, name string) {
	if name == "" {
		return
	}
	companies, err := r.companySrc.FindByName(context.Background(), name)
	if err != nil {
		return
	}
	if companies == nil || len(companies) == 0 {
		return
	}
	company := companies[0]
	target.ProductCompany = &company
}

func (r *WKMapRule) CanOCR(texts *[]string) bool {
	if texts == nil {
		return false
	}
	for _, text := range *texts {
		if strings.Contains(text, "威垦") {
			return true
		}
	}
	return false
}

func (r *WKMapRule) CanExcel(rows *[][]string) bool {
	if rows == nil {
		return false
	}
	for _, row := range *rows {
		for _, text := range row {
			if strings.Contains(text, "威垦") {
				return true
			}
		}
	}
	return false
}

func (r *WKMapRule) MapFromOCR(texts *[]string) (any, errs.StackError) {
	if !r.CanOCR(texts) {
		return nil, nil
	}
	dto := model.ServiceBillDTO{}
	r.SetCompany(&dto, "威垦")
	for _, text := range *texts {
		r.SetByText(&dto, text)
	}
	return &dto, nil
}

// matchNumberPattern 检查字符串是否匹配数字模式
func matchNumberPattern(s string) bool {
	matched, _ := regexp.MatchString(`^\d+\.?\d*$`, s)
	return matched
}

func (r *WKMapRule) MapFromExcel(rows *[][]string) (any, errs.StackError) {
	if !r.CanExcel(rows) {
		return nil, nil
	}

	serviceBill := &model.ServiceBillDTO{
		Details: []model.ServiceBillDetailDTO{},
	}

	// 设置公司
	r.SetCompany(serviceBill, "威垦")

	// 明细开始索引行
	detailStartIndex := -1

	for i, row := range *rows {
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
				detail := model.ServiceBillDetailDTO{}
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
						detail := model.ServiceBillDetailDTO{
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
	return serviceBill, nil
}
