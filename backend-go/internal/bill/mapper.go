package bill

import (
	"backend-go/internal/attach"
	"backend-go/internal/company"
	"backend-go/pkg/errs"
)

// MapRule 服务单映射规则
//
// 仅在本模块内部使用，不对外注册，规则集合由 ServiceBillMapper 自己持有
type MapRule interface {
	CanMapTexts(texts []string) bool
	CanMapGrid(rows [][]string) bool
	MapFromTexts(texts []string) (*ServiceBillDTO, bool, error)
	MapFromGrid(rows [][]string) (*ServiceBillDTO, bool, error)
}

// ServiceBillMapper 服务单映射
//
// 规则集合由本模块自己维护：菱电判定更具体，排在威垦之前
type ServiceBillMapper struct {
	content *attach.ContentService
	rules   []MapRule
}

func NewServiceBillMapper(content *attach.ContentService, companySrc *company.Service) *ServiceBillMapper {
	return &ServiceBillMapper{
		content: content,
		rules: []MapRule{
			newWKMapRule(companySrc, "菱电"),
			newWKMapRule(companySrc, "威垦"),
		},
	}
}

// Map 附件映射为服务单
func (m *ServiceBillMapper) Map(attachment *attach.AttachmentDTO) (*ServiceBillDTO, error) {
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
func (m *ServiceBillMapper) fromTexts(texts []string) (*ServiceBillDTO, error) {
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
func (m *ServiceBillMapper) fromGrid(rows [][]string) (*ServiceBillDTO, error) {
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
