package service

import (
	"backend-go/config"
	"backend-go/internal/common/errs"
	"bytes"
	"io"
	"net/http"

	"github.com/bytedance/sonic"
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
	json, err := sonic.Marshal(OCRRequest{ImageFile: path})
	if err != nil {
		return nil, err
	}
	res, err := http.Post(s.cfg.URL, "application/json", bytes.NewBuffer(json))
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()

	resByte, err := io.ReadAll(res.Body)
	if err != nil {
		return nil, err
	}
	result := make(OCRResult)
	if err = sonic.Unmarshal(resByte, &result); err != nil {
		return nil, err
	}

	var texts []string
	for _, block := range result {
		texts = append(texts, block.RecTxt)
	}

	return &texts, nil
}
