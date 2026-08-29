package attach

import (
	"backend-go/config"
	"backend-go/pkg/errs"
	"path/filepath"
	"time"

	"github.com/gofiber/fiber/v3/client"
	"github.com/gofiber/fiber/v3/log"
)

type OCRService struct {
	cfg *config.OCRConfig
}

type OCRResult struct {
	Code int      `json:"code"`
	Msg  string   `json:"msg"`
	Data []string `json:"data"`
}

func NewOCRService(cfg *config.OCRConfig) *OCRService {
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
	defer client.ReleaseRequest(req)
	file := client.AcquireFile()
	file.SetFieldName("file")
	file.SetPath(path)
	req.AddFiles(file)
	start := time.Now()
	res, err := req.Post(s.cfg.URL)
	if err != nil {
		log.Errorf("OCR 调用失败: %v - %v", s.cfg.URL, err)
		return nil, err
	}
	log.Infof("OCR 调用完成: %v 耗时 %vms", filepath.Base(path), time.Since(start).Milliseconds())
	var ocrResult OCRResult
	if err = res.JSON(&ocrResult); err != nil || res.StatusCode() != 200 {
		return nil, errs.NewBizError(ocrResult.Msg)
	}

	return ocrResult.Data, nil
}
