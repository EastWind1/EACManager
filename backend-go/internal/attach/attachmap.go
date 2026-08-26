package attach

import (
	"backend-go/pkg/cache"
	"backend-go/pkg/errs"
	"context"
	"sync"
)

// MapRule 附件映射规则
type MapRule interface {
	// MapFromTexts 从文本块映射到实体， 返回 nil 表示无法映射
	MapFromTexts(texts []string) (any, error)
	// MapFromGrid 从表格映射到实体, 返回 nil 表示无法映射
	MapFromGrid(rows [][]string) (any, error)
}

// MapService 映射服务
type MapService struct {
	cache         cache.Cache
	ocrService    *OCRService
	attachService *Service
	rules         []MapRule
	lock          sync.RWMutex
}

func NewMapService(cache cache.Cache, ocrService *OCRService, attachService *Service) *MapService {
	return &MapService{
		cache:         cache,
		ocrService:    ocrService,
		attachService: attachService,
		rules:         make([]MapRule, 0),
		lock:          sync.RWMutex{},
	}
}

// RegisterRule 注册映射规则实例
func (s *MapService) RegisterRule(rule MapRule) {
	s.lock.Lock()
	defer s.lock.Unlock()
	s.rules = append(s.rules, rule)
}

func (s *MapService) MapTo(attach *AttachmentDTO) (any, error) {
	if attach == nil {
		return nil, errs.NewBizError("附件为空")
	}
	path, err := s.attachService.GetAbsolutePathById(context.Background(), attach.ID)
	if err != nil {
		return nil, err
	}
	switch attach.Type {
	case Image:
		texts, err := s.ocrService.ParseImage(path)
		if err != nil {
			return nil, err
		}
		for _, rule := range s.rules {
			if cur, err := rule.MapFromTexts(texts); err == nil && cur != nil {
				return cur, nil
			}
		}
		return nil, errs.NewBizError("未配置映射规则")
	case PDF:
		var texts []string
		texts, err = ExtractPDFText(path)
		// 未识别到文本，降级为ocr
		if err != nil || len(texts) == 0 {
			texts, err = s.ocrService.ParseImage(path)
		}
		if err != nil {
			return nil, err
		}
		for _, rule := range s.rules {
			if cur, err := rule.MapFromTexts(texts); err == nil && cur != nil {
				return cur, nil
			}
		}
		return nil, errs.NewBizError("未配置映射规则")
	case Excel:
		rows, err := ParseExcel(path)
		if err != nil {
			return nil, err
		}
		for _, rule := range s.rules {
			if cur, err := rule.MapFromGrid(rows); err == nil && cur != nil {
				return cur, nil
			}
		}
		return nil, errs.NewBizError("未配置映射规则")
	default:
		return nil, errs.NewBizError("不支持的映射文件类型")
	}
}
