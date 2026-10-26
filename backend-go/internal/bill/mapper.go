package bill

import (
	"backend-go/internal/attach"
	"backend-go/internal/company"
	"backend-go/pkg/errs"
)

// MapRule 服务单映射规则
//
// 仅在本模块内部使用，不对外注册，规则集合由 Mapper 自己持有
type MapRule interface {
	CanMapTexts(texts []string) bool
	CanMapGrid(rows [][]string) bool
	MapFromTexts(texts []string) (*DTO, bool, error)
	MapFromGrid(rows [][]string) (*DTO, bool, error)
}

// Mapper 服务单映射
//
// 规则集合由本模块自己维护：菱电判定更具体，排在威垦之前
type Mapper struct {
	content *attach.ContentService
	rules   []MapRule
}

func NewServiceBillMapper(content *attach.ContentService, companySrc *company.Service) *Mapper {
	return &Mapper{
		content: content,
		rules: []MapRule{
			newWKMapRule(companySrc, "菱电"),
			newWKMapRule(companySrc, "威垦"),
		},
	}
}

// Map 附件映射为服务单
func (m *Mapper) Map(attachment *attach.DTO) (*DTO, error) {
	if attachment == nil {
		return nil, errs.NewBizError("附件为空")
	}
	if attachment.Type == attach.Excel {
		rows, err := m.content.Grid(attachment)
		if err != nil {
			return nil, err
		}
		return m.fromGrid(rows)
	}
	texts, err := m.content.Texts(attachment)
	if err != nil {
		return nil, err
	}
	return m.fromTexts(texts)
}

// fromTexts 按本模块规则顺序尝试文本映射，命中即返回；规则解析出错直接上报
func (m *Mapper) fromTexts(texts []string) (*DTO, error) {
	for _, rule := range m.rules {
		if !rule.CanMapTexts(texts) {
			continue
		}
		dto, matched, err := rule.MapFromTexts(texts)
		if err != nil {
			return nil, err
		}
		if matched {
			return dto, nil
		}
	}
	return nil, errs.NewBizError("未配置映射规则")
}

// fromGrid 按本模块规则顺序尝试表格映射，命中即返回；规则解析出错直接上报
func (m *Mapper) fromGrid(rows [][]string) (*DTO, error) {
	for _, rule := range m.rules {
		if !rule.CanMapGrid(rows) {
			continue
		}
		dto, matched, err := rule.MapFromGrid(rows)
		if err != nil {
			return nil, err
		}
		if matched {
			return dto, nil
		}
	}
	return nil, errs.NewBizError("未配置映射规则")
}
