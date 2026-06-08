package attach

import (
	"backend-go/pkg/cache"
	"backend-go/pkg/errs"
	"sync"
)

// MapRule 附件映射规则
type MapRule interface {
	// MapFromOCR 从 OCR 识别结果映射到实体, 返回 nil 表示无法映射
	MapFromOCR(texts []string) (any, error)
	// MapFromExcel 从 Excel 解析结果映射到实体, 返回 nil 表示无法映射
	MapFromExcel(rows [][]string) (any, error)
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
	path, err := s.attachService.GetAbsolutePath(attach.RelativePath, attach.Temp)
	if err != nil {
		return nil, err
	}
	switch attach.Type {
	case Image, PDF:
		if attach.Type == PDF {
			target, err := s.attachService.CreateTempFile(s.cache, "", ".jpg")
			if err != nil {
				return nil, err
			}
			if err = ConvertPDFToImage(path, target); err != nil {
				return nil, err
			}
			path = target
		}
		texts, err := s.ocrService.ParseImage(path)
		if err != nil {
			return nil, err
		}
		s.lock.RLock()
		defer s.lock.RUnlock()
		for _, rule := range s.rules {
			if cur, err := rule.MapFromOCR(texts); err == nil && cur != nil {
				return cur, nil
			}
		}
		return nil, errs.NewBizError("未配置映射规则")
	case Excel:
		rows, err := ParseExcel(path)
		if err != nil {
			return nil, err
		}
		s.lock.RLock()
		defer s.lock.RUnlock()
		for _, rule := range s.rules {
			if cur, err := rule.MapFromExcel(rows); err == nil && cur != nil {
				return cur, nil
			}
		}
		return nil, errs.NewBizError("未配置映射规则")
	default:
		return nil, errs.NewBizError("不支持的映射文件类型")
	}
}
