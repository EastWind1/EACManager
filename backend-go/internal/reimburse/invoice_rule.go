package reimburse

import (
	"regexp"
	"strconv"
	"strings"
)

var spaceRe = regexp.MustCompile(`\s+`)

// MapRule 报销单映射规则 — 仅支持标准税务发票
type MapRule struct{}

func NewMapRule() *MapRule { return &MapRule{} }

// canMap 检测是否为税务发票
func (r *MapRule) canMap(texts []string) bool {
	for _, t := range texts {
		if strings.Contains(t, "发票") || strings.Contains(t, "价税合计") {
			return true
		}
	}
	return false
}

// MapFromTexts 从税务发票结果映射报销单
//
// 解析规则:
//  1. 找到"项目名称"表头行，表头之后、"合  计"之前为明细行
//  2. 每行按空白分割：第 1 项为名称，倒数第 3 项(金额) + 倒数第 1 项(税额) = 明细金额
//  3. "价税合计"行中提取"小写）"之后的金额作为主表总金额
//  4. "备\n注"开头的块之后的文本作为备注
func (r *MapRule) MapFromTexts(texts []string) (any, error) {
	if !r.canMap(texts) {
		return nil, nil
	}

	dto := &DTO{
		Summary: "发票报销",
		Details: []DetailDTO{},
	}
	index := 0
	// 找到项目表头行索引
	headerIdx := -1
	for i, t := range texts {
		if strings.Contains(t, "项目名称") {
			headerIdx = i
			index = i
			break
		}
	}

	// 解析明细行（表头之后、合计之前）
	if headerIdx >= 0 {
		for i := headerIdx + 1; i < len(texts); i++ {
			line := strings.TrimSpace(texts[i])
			if strings.Contains(line, "合") && strings.Contains(line, "计") {
				index = i
				break
			}
			tokens := spaceRe.Split(line, -1)
			// 过滤纯符号行
			if len(tokens) < 4 {
				continue
			}
			detail := DetailDTO{Name: tokens[0]}
			// 倒数第 3 项（金额）+ 最后一项（税额）
			if len(tokens) >= 3 {
				if v3, err := strconv.ParseFloat(tokens[len(tokens)-3], 64); err == nil {
					detail.Amount = v3
				}
				if v1, err := strconv.ParseFloat(tokens[len(tokens)-1], 64); err == nil {
					detail.Amount += v1
				}
			}
			if detail.Name != "" {
				dto.Details = append(dto.Details, detail)
			}
		}
	}

	// 提取价税合计中的总金额（小写）¥xxx
	for i := index; i < len(texts); i++ {
		t := texts[i]
		if strings.Contains(t, "价税合计") {
			if idx := strings.Index(t, "小写）"); idx >= 0 {
				s := strings.TrimSpace(t[idx+len("小写）"):])
				s = strings.TrimLeft(s, "¥￥")
				if v, err := strconv.ParseFloat(s, 64); err == nil {
					dto.TotalAmount = v
				}
			}
			index = i
			break
		}
	}

	// 提取备注：找到包含"备"的行之后（含该行）所有文本
	remarkStart := -1
	for i := index; i < len(texts); i++ {
		t := texts[i]
		line := strings.TrimSpace(t)
		if line == "备" && i+1 < len(texts) && texts[i+1] == "注" {
			remarkStart = i + 2
			break
		}
	}
	if remarkStart >= 0 {
		var remarks []string
		for i := remarkStart; i < len(texts); i++ {
			s := strings.TrimSpace(texts[i])
			if s != "" {
				remarks = append(remarks, s)
			}
		}
		dto.Remark = strings.Join(remarks, "\n")
	}

	return dto, nil
}

// MapFromGrid 始终返回 nil
func (r *MapRule) MapFromGrid(_ [][]string) (any, error) {
	return nil, nil
}
