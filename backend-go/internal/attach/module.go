package attach

import (
	"backend-go/internal/security"
	"backend-go/pkg/core"
	"context"
	"time"

	"github.com/gofiber/fiber/v3/log"
)

// Module 聚合附件模块对外服务。
type Module struct {
	Service *Service
	Content *ContentService
}

func Setup(base *core.App, sec *security.Module) *Module {
	sec.PermissionSync.Register(attachPermissions()...)
	attachmentRepo := newRepository(base.DB)
	attachmentService := newService(base.Config.Attach, attachmentRepo)
	ocrService := newOCRService(base.Config.OCR)
	attachmentController := newController(attachmentService)
	attachGroup := base.Router.Group("/attachment")
	{
		attachGroup.Get("/", sec.Middleware(DownloadAuthCode), attachmentController.Download)
		attachGroup.Post("/upload", sec.Middleware(UploadAuthCode), attachmentController.Upload)
	}

	go func() {
		ticker := time.NewTicker(24 * time.Hour)
		defer ticker.Stop()
		for range ticker.C {
			if err := attachmentService.CleanTransientFiles(context.Background()); err != nil {
				log.Errorf("回收游离附件失败: %v", err)
			}
		}
	}()

	return &Module{
		Service: attachmentService,
		Content: newContentService(ocrService, attachmentService),
	}
}
