package attach

import (
	"backend-go/config"
	"backend-go/pkg/errs"

	"github.com/bytedance/sonic"
	"github.com/gofiber/fiber/v3/client"
)

type OCRBlock struct {
	RecTxt  string  `json:"rec_txt"`
	DTBoxes [][]int `json:"dt_boxes"`
	Score   float32 `json:"score"`
}

type OCRService struct {
	cfg *config.OCRConfig
}

func NewOCRService(cfg *config.OCRConfig) *OCRService {
	c := client.New()
	c.SetJSONMarshal(sonic.Marshal)
	c.SetJSONUnmarshal(sonic.Unmarshal)
	return &OCRService{
		cfg: cfg,
	}
}

// ParseImage 解析图片
func (s *OCRService) ParseImage(path string) ([]string, error) {
	if s.cfg == nil || s.cfg.URL == "" {
		return nil, errs.NewBizError("未配置 OCR 服务器")
	}
	req := client.AcquireRequest()
	file := client.AcquireFile()
	file.SetFieldName("file")
	file.SetPath(path)
	req.AddFiles(file)
	res, err := req.Post(s.cfg.URL)
	if err != nil {
		return nil, err
	}
	if res.StatusCode() != 200 {
		return nil, errs.NewBizError(res.Status())
	}
	var ocrResult []string
	if err = res.JSON(&ocrResult); err != nil {
		return nil, err
	}

	return ocrResult, nil
}
