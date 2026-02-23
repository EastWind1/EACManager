package service

import (
	"backend-go/config"
	"backend-go/internal/pkg/errs"

	"github.com/bytedance/sonic"
	"github.com/gofiber/fiber/v3/client"
)

type OCRRequest struct {
	ImageFile string `json:"image_file"`
}

type OCRBlock struct {
	RecTxt  string  `json:"rec_txt"`
	DTBoxes [][]int `json:"dt_boxes"`
	Score   float32 `json:"score"`
}

type OCRResult map[string]OCRBlock

type OCRService struct {
	cfg    *config.OCRConfig
	client *client.Client
}

func NewOCRService(cfg *config.OCRConfig) *OCRService {
	c := client.New()
	c.SetJSONMarshal(sonic.Marshal)
	c.SetJSONUnmarshal(sonic.Unmarshal)
	return &OCRService{
		cfg:    cfg,
		client: c,
	}
}

// ParseImage 解析图片
func (s *OCRService) ParseImage(path string) ([]string, error) {
	if s.cfg.URL == "" {
		return nil, errs.NewBizError("未配置 OCR 服务器")
	}
	res, err := s.client.Post(s.cfg.URL, client.Config{
		Body: OCRRequest{ImageFile: path},
	})
	if err != nil {
		return nil, err
	}
	ocrResult := make(OCRResult)
	if err = res.JSON(&ocrResult); err != nil {
		return nil, err
	}
	var texts []string
	for _, block := range ocrResult {
		texts = append(texts, block.RecTxt)
	}

	return texts, nil
}
