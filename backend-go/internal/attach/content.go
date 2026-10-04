package attach

import (
	"backend-go/pkg/errs"
	"context"
)

// ContentService 附件内容读取：图片/PDF 取文本块，Excel 取表格
//
// 仅负责把附件变成文本或表格，映射到具体实体由各业务模块自己完成
type ContentService struct {
	ocrService    *OCRService
	attachService *Service
}

func NewContentService(ocrService *OCRService, attachService *Service) *ContentService {
	return &ContentService{
		ocrService:    ocrService,
		attachService: attachService,
	}
}

// Texts 读取文本块：图片走 OCR；PDF 先取文本层，未识别到文本时降级 OCR
func (s *ContentService) Texts(attachment *AttachmentDTO) ([]string, error) {
	path, err := s.attachService.GetAbsolutePathById(context.Background(), attachment.ID)
	if err != nil {
		return nil, err
	}
	switch attachment.Type {
	case Image:
		return s.ocrService.ParseImage(path)
	case PDF:
		texts, err := ExtractPDFText(path)
		// 未识别到文本，降级为ocr
		if err != nil || len(texts) == 0 {
			return s.ocrService.ParseImage(path)
		}
		return texts, nil
	default:
		return nil, errs.NewBizError("不支持解析为文本的附件类型")
	}
}

// Grid 读取表格：仅 Excel
func (s *ContentService) Grid(attachment *AttachmentDTO) ([][]string, error) {
	path, err := s.attachService.GetAbsolutePathById(context.Background(), attachment.ID)
	if err != nil {
		return nil, err
	}
	if attachment.Type != Excel {
		return nil, errs.NewBizError("不支持解析为表格的附件类型")
	}
	return ParseExcel(path)
}
