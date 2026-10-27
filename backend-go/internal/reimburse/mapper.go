package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/pkg/errs"
)

// Mapper 仅支持标准税务发票（文本），规则由本模块自己持有。
type Mapper struct {
	content *attach.ContentService
	rule    *MapRule
}

func NewMapper(content *attach.ContentService) *Mapper {
	return &Mapper{
		content: content,
		rule:    NewMapRule(),
	}
}

func (m *Mapper) Map(attachment *attach.DTO) (*DTO, error) {
	if attachment == nil {
		return nil, errs.NewBizError("附件为空")
	}
	texts, err := m.content.Texts(attachment)
	if err != nil {
		return nil, err
	}
	dto, matched, err := m.rule.MapFromTexts(texts)
	if err != nil {
		return nil, err
	}
	if !matched {
		return nil, errs.NewBizError("未配置映射规则")
	}
	return dto, nil
}
