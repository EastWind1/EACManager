package service

import "strings"

type LDMapRule struct {
	*WKMapRule
}

func NewLDMapRule(wkMapRule *WKMapRule) *LDMapRule {
	return &LDMapRule{
		WKMapRule: wkMapRule,
	}
}

func (r *LDMapRule) CanOCR(texts *[]string) bool {
	if texts == nil {
		return false
	}
	for _, text := range *texts {
		if strings.Contains(text, "菱电") {
			return true
		}
	}
	return false
}

func (r *LDMapRule) CanExcel(rows *[][]string) bool {
	if rows == nil {
		return false
	}
	for _, row := range *rows {
		for _, text := range row {
			if strings.Contains(text, "菱电") {
				return true
			}
		}
	}
	return false
}
