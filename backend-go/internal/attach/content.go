package attach

import (
	"backend-go/pkg/errs"
	"context"
)

// ContentService 附件内容读取：图片/PDF 取文本块，Excel 取表格
type ContentService struct {
	ocrService    *OCRService
	attachService *Service
}

func newContentService(ocrService *OCRService, attachService *Service) *ContentService {
	return &ContentService{
		ocrService:    ocrService,
		attachService: attachService,
	}
}

// Texts 读取文本块：图片走 OCR；PDF 先取文本层，未识别到文本时降级 OCR
func (s *ContentService) Texts(ctx context.Context, attachment *DTO) ([]string, error) {
	readable, err := s.attachService.GetPath(ctx, attachment.ID)
	if err != nil {
		return nil, err
	}
	switch readable.Attachment.Type {
	case Image:
		return s.ocrService.ParseImage(readable.Path)
	case PDF:
		texts, err := ExtractPDFText(readable.Path)
		// 未识别到文本，降级为ocr
		if err != nil || len(texts) == 0 {
			return s.ocrService.ParseImage(readable.Path)
		}
		return texts, nil
	default:
		return nil, errs.NewBizError("不支持解析为文本的附件类型")
	}
}

// Grid 读取表格：仅 Excel
func (s *ContentService) Grid(ctx context.Context, attachment *DTO) ([][]string, error) {
	readable, err := s.attachService.GetPath(ctx, attachment.ID)
	if err != nil {
		return nil, err
	}
	if readable.Attachment.Type != Excel {
		return nil, errs.NewBizError("不支持解析为表格的附件类型")
	}
	return ParseExcel(readable.Path)
}
