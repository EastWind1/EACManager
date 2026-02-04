package service

import (
	"backend-go/config"
	"backend-go/internal/common/errs"

	"github.com/bytedance/sonic"
	"github.com/gofiber/fiber/v2"
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
	cfg *config.OCRConfig
}

func NewOCRService(cfg *config.OCRConfig) *OCRService {
	return &OCRService{
		cfg: cfg,
	}
}

// ParseImage 解析图片
func (s *OCRService) ParseImage(path string) (*[]string, error) {
	if s.cfg.URL == "" {
		return nil, errs.NewBizError("未配置 OCR 服务器")
	}
	client := fiber.Post(s.cfg.URL)
	client.JSON(OCRRequest{ImageFile: path})
	_, body, e := client.Bytes()
	if len(e) > 0 {
		return nil, e[0]
	}

	res := make(OCRResult)
	if err := sonic.Unmarshal(body, &res); err != nil {
		return nil, err
	}
	var texts []string
	for _, block := range res {
		texts = append(texts, block.RecTxt)
	}

	return &texts, nil
}
