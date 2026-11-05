package attach

import (
	"backend-go/config"
	"backend-go/internal/security"
	"context"
	"time"

	"github.com/gofiber/fiber/v3"
	"github.com/gofiber/fiber/v3/log"
	"gorm.io/gorm"
)

func Setup(cfg *config.Config, db *gorm.DB, router fiber.Router, permissionSrv *security.Service,
	permissionSyncSrv *security.PermissionSyncService) (*Service, *ContentService) {
	permissionSyncSrv.Register(attachPermissions()...)
	attachmentRepo := newRepository(db)
	attachmentService := newService(cfg.Attach, attachmentRepo)
	ocrService := newOCRService(cfg.OCR)
	attachmentController := newController(attachmentService)
	attachGroup := router.Group("/attachment")
	{
		attachGroup.Get("/", security.Middleware(permissionSrv, DownloadAuthCode), attachmentController.Download)
		attachGroup.Post("/upload", security.Middleware(permissionSrv, UploadAuthCode), attachmentController.Upload)
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

	return attachmentService, newContentService(ocrService, attachmentService)
}
