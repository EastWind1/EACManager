package service

import (
	"backend-go/internal/attach/files"
	"backend-go/internal/attach/model"
	"backend-go/internal/common/cache"
	"backend-go/internal/common/errs"
	"sync"
)

// AttachMapRule 附件映射规则
type AttachMapRule interface {
	// MapFromOCR 从 OCR 识别结果映射到实体, 返回 nil 表示无法映射
	MapFromOCR(texts *[]string) (any, error)
	// MapFromExcel 从 Excel 解析结果映射到实体, 返回 nil 表示无法映射
	MapFromExcel(rows *[][]string) (any, error)
}

// AttachMapService 映射服务
type AttachMapService struct {
	cache         cache.Cache
	ocrService    *OCRService
	attachService *AttachmentService
	rules         []AttachMapRule
	lock          sync.RWMutex
}

func NewAttachMapService(cache cache.Cache, ocrService *OCRService, attachService *AttachmentService) *AttachMapService {
	return &AttachMapService{
		cache:         cache,
		ocrService:    ocrService,
		attachService: attachService,
		rules:         make([]AttachMapRule, 0),
		lock:          sync.RWMutex{},
	}
}

// RegisterRule 注册映射规则实例
func (s *AttachMapService) RegisterRule(rule AttachMapRule) {
	s.lock.Lock()
	defer s.lock.Unlock()
	s.rules = append(s.rules, rule)
}

func (s *AttachMapService) MapTo(attach *model.AttachmentDTO) (any, error) {
	if attach == nil {
		return nil, errs.NewBizError("附件为空")
	}
	path, err := s.attachService.GetAbsolutePath(attach.RelativePath, attach.Temp)
	if err != nil {
		return nil, err
	}
	switch attach.Type {
	case model.AttachTypeImage, model.AttachTypePDF:
		if attach.Type == model.AttachTypePDF {
			target, err := s.attachService.CreateTempFile(s.cache, "", ".jpg")
			if err != nil {
				return nil, err
			}
			if err = files.ConvertPDFToImage(path, target); err != nil {
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
	case model.AttachTypeExcel:
		rows, err := files.ParseExcel(path)
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
